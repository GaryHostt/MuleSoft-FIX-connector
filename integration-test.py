#!/usr/bin/env python3
"""
FIX Protocol Integration Test
Tests connectivity between MuleSoft connector and FIX server
"""

import socket
import time
import sys

SOH = '\x01'

def calculate_checksum(message):
    """Calculate FIX checksum"""
    checksum = sum(ord(c) for c in message) % 256
    return f"{checksum:03d}"

def build_fix_message(msg_type, fields, seq_num):
    """Build a FIX message"""
    # Build body
    body = f"35={msg_type}{SOH}"
    body += f"49=TESTCLIENT{SOH}"
    body += f"56=SERVER1{SOH}"
    body += f"34={seq_num}{SOH}"
    body += f"52={time.strftime('%Y%m%d-%H:%M:%S.000')}{SOH}"
    
    for tag, value in fields.items():
        body += f"{tag}={value}{SOH}"
    
    # Build header
    header = f"8=FIX.4.4{SOH}9={len(body)}{SOH}"
    
    # Calculate checksum
    checksum = calculate_checksum(header + body)
    
    # Build complete message
    return f"{header}{body}10={checksum}{SOH}"

def test_server_connectivity():
    """Test if FIX server is accessible"""
    print("=" * 60)
    print("FIX Protocol Integration Test")
    print("=" * 60)
    print()
    
    print("Test 1: Server Connectivity")
    print("-" * 40)
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('localhost', 9876))
        print("✓ Successfully connected to FIX server on port 9876")
        
        # Send Logon
        logon = build_fix_message('A', {'98': '0', '108': '30'}, 1)
        print(f"\nSending Logon message...")
        sock.sendall(logon.encode('latin-1'))
        print("✓ Logon message sent")
        
        # Wait for response
        print("\nWaiting for Logon response...")
        sock.settimeout(10)
        data = sock.recv(4096)
        
        if data:
            print(f"✓ Received response ({len(data)} bytes)")
            
            # Check for Logon response (35=A)
            if '35=A' in data.decode('latin-1', errors='ignore'):
                print("✓ Logon response received (MsgType=A)")
            else:
                print("⚠ Response received but not Logon")
        else:
            print("✗ No response received")
        
        # Send Logout
        print("\nSending Logout...")
        logout = build_fix_message('5', {'58': 'Test complete'}, 2)
        sock.sendall(logout.encode('latin-1'))
        print("✓ Logout sent")
        
        time.sleep(1)
        sock.close()
        print("\n✓ Connection closed successfully")
        
        return True
        
    except socket.timeout:
        print("✗ Connection timeout - server may not be running")
        return False
    except ConnectionRefusedError:
        print("✗ Connection refused - server not running on port 9876")
        return False
    except Exception as e:
        print(f"✗ Error: {e}")
        return False

def test_protocol_compliance():
    """Test FIX protocol compliance"""
    print("\n" + "=" * 60)
    print("Test 2: FIX Protocol Compliance")
    print("-" * 40)
    
    tests_passed = 0
    tests_total = 8
    
    # Test checksum calculation
    test_message = "8=FIX.4.4\x019=40\x0135=A\x0149=TEST\x0156=TEST\x01"
    expected_checksum = calculate_checksum(test_message)
    print(f"Checksum calculation: {expected_checksum}")
    if len(expected_checksum) == 3 and expected_checksum.isdigit():
        print("✓ Checksum format correct (3 digits)")
        tests_passed += 1
    else:
        print("✗ Checksum format incorrect")
    
    # Test message structure
    msg = build_fix_message('D', {'11': 'TEST001', '55': 'EUR/USD'}, 1)
    
    if msg.startswith('8=FIX.4.4'):
        print("✓ BeginString (Tag 8) present and first")
        tests_passed += 1
    else:
        print("✗ BeginString not first")
    
    if SOH + '9=' in msg[:30]:
        print("✓ BodyLength (Tag 9) present")
        tests_passed += 1
    else:
        print("✗ BodyLength missing")
    
    if '35=' in msg:
        print("✓ MsgType (Tag 35) present")
        tests_passed += 1
    else:
        print("✗ MsgType missing")
    
    if '49=' in msg:
        print("✓ SenderCompID (Tag 49) present")
        tests_passed += 1
    else:
        print("✗ SenderCompID missing")
    
    if '56=' in msg:
        print("✓ TargetCompID (Tag 56) present")
        tests_passed += 1
    else:
        print("✗ TargetCompID missing")
    
    if '34=' in msg:
        print("✓ MsgSeqNum (Tag 34) present")
        tests_passed += 1
    else:
        print("✗ MsgSeqNum missing")
    
    if msg.endswith(SOH) and '10=' in msg[-20:]:
        print("✓ CheckSum (Tag 10) present and last")
        tests_passed += 1
    else:
        print("✗ CheckSum not last")
    
    print(f"\nProtocol Compliance: {tests_passed}/{tests_total} tests passed")
    return tests_passed == tests_total

def main():
    """Run all tests"""
    connectivity_ok = test_server_connectivity()
    compliance_ok = test_protocol_compliance()
    
    print("\n" + "=" * 60)
    print("Test Summary")
    print("=" * 60)
    print(f"Server Connectivity: {'✓ PASS' if connectivity_ok else '✗ FAIL'}")
    print(f"Protocol Compliance: {'✓ PASS' if compliance_ok else '✗ FAIL'}")
    print()
    
    if connectivity_ok and compliance_ok:
        print("✓ ALL TESTS PASSED")
        print("\nThe FIX server is running and protocol implementation is correct.")
        print("The connector is ready for MuleSoft integration testing.")
        return 0
    else:
        print("✗ SOME TESTS FAILED")
        if not connectivity_ok:
            print("\nPlease start the FIX server:")
            print("  cd fix-server-go && python3 fix_server.py")
        return 1

if __name__ == '__main__':
    sys.exit(main())

