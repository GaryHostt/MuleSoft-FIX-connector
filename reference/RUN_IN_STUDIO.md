# 🚀 Run FIX Connector in Anypoint Studio

## ✅ Prerequisites Complete

- [x] Connector built with Java 17 support (SDK 1.9.0)
- [x] Installed to local Maven repo
- [x] Mule app configured for runtime 4.10.0

## Step-by-Step Instructions

### 1. Start the FIX Server

Open a terminal and start the Python FIX server:

```bash
cd /Users/alex.macdonald/fix-connector/fix-server-go
python3 fix_server.py
```

You should see:
```
FIX 4.4 Server listening on localhost:9876
```

**Keep this terminal open** - the server must remain running.

---

### 2. Open/Import Project in Anypoint Studio

If not already imported:

1. **Open Anypoint Studio**
2. **File → Import → Anypoint Studio → Anypoint Studio project from File System**
3. **Project Root:** `/Users/alex.macdonald/fix-connector/fix-sample-app`
4. Click **Finish**

---

### 3. Configure Mule Runtime (if needed)

1. **Right-click** the project → **Properties**
2. **Mule → Mule Runtime**
3. Ensure **Mule Server 4.10.0** is selected
4. If not available, download it:
   - **Help → Install New Software**
   - Or download from: https://www.mulesoft.com/lp/dl/mule-esb-enterprise

---

### 4. Update Project Dependencies

The connector was just rebuilt, so Studio needs to refresh:

1. **Right-click** project → **Mule → Update Project Mule Runtime**
2. Or: **Right-click** → **Maven → Update Project** → Check "Force Update of Snapshots/Releases"

---

### 5. Clean and Build

1. **Right-click** project → **Mule → Clean**
2. **Project → Clean** → Select "fix-sample-app" → **Clean**

---

### 6. Run the Application

1. **Right-click** `src/main/mule/fix-sample-app.xml`
2. **Run As → Mule Application (configure)**
3. In the Run Configuration:
   - **Mule Runtime**: 4.10.0
   - **Java Version**: 17 (Important!)
4. Click **Run**

Watch the Console output. You should see:

```
***********************************************************************
*            - - + APPLICATION + - -            * FIX Sample Application  *
***********************************************************************
**********************************************************************
*              - - + DOMAIN + - -               * default             *
**********************************************************************
**********************************************************************
* Started app 'fix-sample-app'                                       *
**********************************************************************
```

---

### 7. Test Connection with Postman

#### Import Collection & Environment:

1. Open **Postman**
2. **Import** → Select files:
   - `/Users/alex.macdonald/fix-connector/FIX_Connector_Tests.postman_collection.json`
   - `/Users/alex.macdonald/fix-connector/FIX_Connector_Local.postman_environment.json`
3. Select environment: **FIX Connector - Local**

#### Run Tests:

**Test 1: FIX Logon**
```
POST http://localhost:8081/fix/logon
```
Expected response:
```json
{
  "success": "true",
  "msgType": "A",
  "seqNum": "1"
}
```

**Test 2: Send Heartbeat**
```
POST http://localhost:8081/fix/heartbeat
```

**Test 3: Send New Order Single**
```
POST http://localhost:8081/fix/order
Body:
{
  "clOrdId": "ORDER123",
  "symbol": "AAPL",
  "side": "1",
  "orderQty": "100",
  "ordType": "2",
  "price": "150.50"
}
```

**Test 4: Get Session Info**
```
GET http://localhost:8081/fix/session
```

---

## 🔍 Troubleshooting

### Issue: "The component 'fix:config' doesn't belong to any extension model"

**Solution:** The connector needs to be in your local Maven repo. Rebuild it:
```bash
cd /Users/alex.macdonald/fix-connector/mulesoft-fix-connector
mvn clean install -DskipTests
```
Then refresh the Mule app in Studio (Step 4 above).

---

### Issue: "java.lang.NoClassDefFoundError" or "InaccessibleObjectException"

**Solution:** Ensure you're using:
- **Java 17** (not Java 11 or 8)
- **Mule Runtime 4.10.0** (not 4.6 or earlier)

To check Java version in Studio:
- **Window → Preferences → Java → Installed JREs**
- Ensure JDK 17 is selected

---

### Issue: "Connection refused" when sending requests

**Solution:** 
1. Check that the FIX server is running (Step 1)
2. Check that the Mule app successfully started (no errors in Console)
3. Verify the HTTP listener is on port 8081

---

### Issue: Build fails with "minMuleVersion" error

**Solution:** The connector's `mule-artifact.json` has been updated to require 4.9.0+. Ensure you rebuilt the connector (see above).

---

## 📊 Expected Flow

```
Postman Request → Mule HTTP Listener → FIX Connector Operation → TCP Socket → FIX Server
                                                                                 ↓
                                                                             Logon/ACK
                                                                                 ↓
Postman Response ← Transform to JSON ← Parse FIX Response ← TCP Socket ← ExecutionReport
```

---

## 🎯 Success Criteria

✅ Mule app starts without errors  
✅ HTTP listener active on port 8081  
✅ FIX connector connects to server on port 9876  
✅ Logon message sent successfully  
✅ Session established with sequence numbers  
✅ New Order Single message sends and receives ExecutionReport  
✅ Heartbeat mechanism works  
✅ Session info retrieval works  

---

## 📝 Notes

- **First Request Delay**: The first request may take 2-3 seconds as the FIX session establishes. Subsequent requests will be faster.
- **Sequence Numbers**: The connector maintains sequence numbers across messages. If you restart the server, you may need to restart the Mule app to reset sequences.
- **Logs**: Check the Studio Console for detailed FIX protocol messages and any errors.

---

## 🚀 You're All Set!

The connector is now properly configured with:
- ✅ Java 17 support via MuleSoft SDK 1.9.0
- ✅ Mule Runtime 4.10 compatibility  
- ✅ Full FIX Protocol implementation
- ✅ Session management with Object Store simulation
- ✅ Heartbeat and sequence number handling

Happy testing! 🎉

