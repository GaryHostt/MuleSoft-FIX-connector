#!/usr/bin/env python3
"""
Simple FIX Protocol Server in Python
Alternative to Go server for testing the MuleSoft FIX Connector
Requires Python 3.7+
"""

import socket
import threading
import time
from datetime import datetime

SOH = '\x01'  # Start of Header delimiter

class FIXSession:
    def __init__(self, conn, addr):
        self.conn = conn
        self.addr = addr
        self.sender_comp_id = "SERVER1"
        self.target_comp_id = "CLIENT1"
        self.incoming_seq_num = 1
        self.outgoing_seq_num = 1
        self.heartbeat_interval = 30
        self.logged_in = False
        self.last_message_time = time.time()
        
    def process_message(self, raw_message):
        """Process incoming FIX message"""
        self.last_message_time = time.time()
        
        msg = self.parse_message(raw_message)
        if not msg:
            print("Failed to parse message")
            return
            
        msg_type = msg.get(35, '')
        seq_num = int(msg.get(34, 0))
        
        print(f"Received: MsgType={msg_type}, SeqNum={seq_num}")
        
        # Validate sequence number
        if seq_num != self.incoming_seq_num:
            print(f"Sequence mismatch: expected={self.incoming_seq_num}, received={seq_num}")
        self.incoming_seq_num += 1
        
        # Handle message by type
        if msg_type == 'A':  # Logon
            self.handle_logon(msg)
        elif msg_type == '5':  # Logout
            self.handle_logout(msg)
        elif msg_type == '0':  # Heartbeat
            print("Received Heartbeat")
        elif msg_type == '1':  # TestRequest
            self.handle_test_request(msg)
        elif msg_type == 'D':  # NewOrderSingle
            self.handle_new_order(msg)
        else:
            print(f"Unhandled message type: {msg_type}")
    
    def handle_logon(self, msg):
        """Handle Logon message"""
        self.target_comp_id = msg.get(49, 'CLIENT1')
        self.sender_comp_id = msg.get(56, 'SERVER1')
        self.heartbeat_interval = int(msg.get(108, 30))
        
        print(f"Logon from {self.target_comp_id} to {self.sender_comp_id}, HeartbeatInterval={self.heartbeat_interval}")
        
        self.logged_in = True
        
        # Send Logon response
        response = self.build_message('A', {
            98: '0',  # EncryptMethod (None)
            108: str(self.heartbeat_interval)
        })
        
        self.send_message(response)
        print("Sent Logon response")
    
    def handle_logout(self, msg):
        """Handle Logout message"""
        text = msg.get(58, '')
        print(f"Logout received: {text}")
        
        # Send Logout response
        response = self.build_message('5', {58: 'Goodbye'})
        self.send_message(response)
        self.logged_in = False
        print("Sent Logout response")
    
    def handle_test_request(self, msg):
        """Handle TestRequest message"""
        test_req_id = msg.get(112, '')
        print(f"TestRequest received: {test_req_id}")
        
        # Respond with Heartbeat containing TestReqID
        response = self.build_message('0', {112: test_req_id})
        self.send_message(response)
        print(f"Sent Heartbeat response to TestRequest: {test_req_id}")
    
    def handle_new_order(self, msg):
        """Handle NewOrderSingle message"""
        cl_ord_id = msg.get(11, '')
        symbol = msg.get(55, '')
        side = msg.get(54, '')
        order_qty = msg.get(38, '')
        price = msg.get(44, '')
        
        print(f"NewOrderSingle: ClOrdID={cl_ord_id}, Symbol={symbol}, Side={side}, Qty={order_qty}, Price={price}")
        
        # Send ExecutionReport (simulated fill)
        exec_report = self.build_message('8', {
            37: 'EXEC' + cl_ord_id,        # OrderID
            11: cl_ord_id,                 # ClOrdID
            17: 'EXEC' + cl_ord_id + '001', # ExecID
            150: '2',                      # ExecType (Fill)
            39: '2',                       # OrdStatus (Filled)
            55: symbol,                    # Symbol
            54: side,                      # Side
            38: order_qty,                 # OrderQty
            44: price,                     # Price
            32: order_qty,                 # LastQty
            31: price,                     # LastPx
            151: '0',                      # LeavesQty
            14: order_qty,                 # CumQty
            6: price,                      # AvgPx
            60: datetime.now().strftime('%Y%m%d-%H:%M:%S.%f')[:-3]  # TransactTime
        })
        
        self.send_message(exec_report)
        print(f"Sent ExecutionReport for order {cl_ord_id}")
    
    def build_message(self, msg_type, fields):
        """Build FIX message"""
        # Build body
        body = f"35={msg_type}{SOH}"
        body += f"49={self.sender_comp_id}{SOH}"
        body += f"56={self.target_comp_id}{SOH}"
        body += f"34={self.outgoing_seq_num}{SOH}"
        body += f"52={datetime.now().strftime('%Y%m%d-%H:%M:%S.%f')[:-3]}{SOH}"
        
        # Add custom fields
        for tag, value in fields.items():
            body += f"{tag}={value}{SOH}"
        
        # Build header
        header = f"8=FIX.4.4{SOH}9={len(body)}{SOH}"
        
        # Calculate checksum
        message_for_checksum = header + body
        checksum = self.calculate_checksum(message_for_checksum)
        
        # Build complete message
        complete_message = f"{message_for_checksum}10={checksum}{SOH}"
        
        self.outgoing_seq_num += 1
        
        return complete_message
    
    def send_message(self, message):
        """Send FIX message"""
        try:
            self.conn.sendall(message.encode('latin-1'))
            self.last_message_time = time.time()
        except Exception as e:
            print(f"Error sending message: {e}")
    
    def parse_message(self, raw_message):
        """Parse FIX message"""
        msg = {}
        fields = raw_message.split(SOH)
        
        for field in fields:
            if not field or '=' not in field:
                continue
            
            parts = field.split('=', 1)
            if len(parts) == 2:
                try:
                    tag = int(parts[0])
                    msg[tag] = parts[1]
                except ValueError:
                    continue
        
        return msg
    
    def calculate_checksum(self, message):
        """Calculate FIX checksum"""
        checksum = sum(ord(c) for c in message) % 256
        return f"{checksum:03d}"
    
    def heartbeat_monitor(self):
        """Monitor and send heartbeats"""
        while self.logged_in:
            time.sleep(5)
            
            if not self.logged_in:
                break
            
            time_since_last = time.time() - self.last_message_time
            
            if time_since_last >= self.heartbeat_interval:
                heartbeat = self.build_message('0', {})
                self.send_message(heartbeat)
                print("Sent scheduled Heartbeat")

def handle_client(conn, addr):
    """Handle client connection"""
    print(f"New connection from {addr}")
    
    session = FIXSession(conn, addr)
    
    # Start heartbeat monitor
    heartbeat_thread = threading.Thread(target=session.heartbeat_monitor, daemon=True)
    heartbeat_thread.start()
    
    buffer = ""
    
    try:
        while True:
            data = conn.recv(4096)
            if not data:
                break
            
            buffer += data.decode('latin-1')
            
            # Check if we have complete message (ends with checksum)
            if SOH + '10=' in buffer:
                # Find complete message
                checksum_idx = buffer.rfind(SOH + '10=')
                if checksum_idx != -1:
                    after_checksum = buffer[checksum_idx + 4:]
                    soh_idx = after_checksum.find(SOH)
                    if soh_idx != -1:
                        complete_message = buffer[:checksum_idx + 4 + soh_idx + 1]
                        session.process_message(complete_message)
                        buffer = buffer[checksum_idx + 4 + soh_idx + 1:]
    
    except Exception as e:
        print(f"Connection error: {e}")
    finally:
        conn.close()
        print(f"Connection closed: {addr}")

def main():
    """Main server function"""
    HOST = 'localhost'
    PORT = 9876
    
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(5)
    
    print(f"FIX Server started on port {PORT}")
    print("Waiting for connections...")
    
    try:
        while True:
            conn, addr = server.accept()
            client_thread = threading.Thread(target=handle_client, args=(conn, addr), daemon=True)
            client_thread.start()
    except KeyboardInterrupt:
        print("\nShutting down server...")
    finally:
        server.close()

if __name__ == '__main__':
    main()

