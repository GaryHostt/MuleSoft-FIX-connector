# Running Your FIX Mule Application

## Current Status

I've discovered you have **Anypoint Studio** installed at:
```
/Applications/AnypointStudio.app
```

This is **much easier** than using standalone Mule Runtime! Let me show you how to run your app and test with Postman.

## ✅ Quick Start: Using Anypoint Studio

### Step 1: Open Anypoint Studio

1. Launch Anypoint Studio from your Applications folder
2. Wait for it to fully load

### Step 2: Import the Project

1. In Studio, go to: **File → Import...**
2. Select: **Anypoint Studio → Anypoint Studio project from File System**
3. Click **Next**
4. **Project Root**: Browse to `/Users/alex.macdonald/fix-connector/fix-sample-app`
5. Click **Finish**

### Step 3: Let Studio Resolve Dependencies

Studio will automatically:
- Download required dependencies
- Find the FIX connector in your local Maven repo (`~/.m2/repository/`)
- Build the project

This may take a minute the first time.

### Step 4: Run the Application

1. In the **Package Explorer**, find `fix-sample-app`
2. Right-click on the project
3. Select: **Run As → Mule Application**
4. Watch the **Console** tab at the bottom

You should see:
```
****************************************************
*            - - + APPLICATION + - -               *
*  fix-sample-app                                  *
****************************************************
...
**********************************************************************
*              - - + DOMAIN + - -            * - - + STATUS + - - *
**********************************************************************
* default                                    * DEPLOYED           *
**********************************************************************

**********************************************************************
* - - + APPLICATION + - -   *       - - + DOMAIN + - -  * - - + STATUS
**********************************************************************
* fix-sample-app            * default                    * DEPLOYED
**********************************************************************
```

### Step 5: Verify It's Running

The HTTP listener will be on **port 8081**. You should see logs like:
```
INFO  [....] HTTP Listener config has been started on host 0.0.0.0 and port 8081
```

## 🧪 Testing with Postman

Now that your app is running, let's test it!

### Step 1: Import Postman Collection

1. Open **Postman**
2. Click **Import** (top left)
3. Select **File** tab
4. Browse to: `/Users/alex.macdonald/fix-connector/FIX_Connector_Tests.postman_collection.json`
5. Click **Open**

### Step 2: Import Environment

1. Click the **Environment** dropdown (top right, shows "No Environment")
2. Click **Import**
3. Browse to: `/Users/alex.macdonald/fix-connector/FIX_Connector_Local.postman_environment.json`
4. Click **Open**
5. Select **FIX Connector Local** from the environment dropdown

### Step 3: Run Tests

The collection has 8 requests. Run them in order:

1. **POST Logon to FIX Server**
   - Establishes FIX session
   - Should return session info

2. **POST Send Heartbeat**
   - Sends heartbeat message
   - Should return success

3. **POST Send Test Request**
   - Sends test request
   - Should get TestRequest confirmation

4. **POST Send New Order Single**
   - Sends a new order
   - Should return order details

5. **POST Request Message Resend**
   - Requests message resend
   - Should return resend request confirmation

6. **GET Session Info**
   - Gets current session state
   - Should return sequence numbers, status

7. **POST Reset Sequence Numbers**
   - Resets sequences to 1
   - Should return confirmation

8. **POST Logout from FIX Server**
   - Closes FIX session
   - Should return logout confirmation

### Expected Results

Each request should return JSON like:
```json
{
  "success": true,
  "message": "Logon successful",
  "sessionInfo": {
    "sessionId": "CLIENT1-SERVER1",
    "status": "LOGGED_IN",
    "outgoingSeqNum": 2,
    "incomingSeqNum": 1
  }
}
```

## 📊 Monitoring

### In Anypoint Studio Console

Watch for FIX messages:
```
INFO  Sending FIX message: 8=FIX.4.4...
INFO  Received FIX message: 8=FIX.4.4...
```

### FIX Server Logs

The FIX server is running in your terminal. You should see:
```
[2025-12-23 17:XX:XX] Accepted connection from ('127.0.0.1', XXXXX)
[2025-12-23 17:XX:XX] Received LOGON
[2025-12-23 17:XX:XX] Sent LOGON response
```

## 🔧 Troubleshooting

### Issue: "Port 8081 already in use"

**Solution:**
```bash
lsof -ti:8081 | xargs kill -9
```

Then restart the Mule app in Studio.

### Issue: "Cannot connect to FIX server"

**Check if FIX server is running:**
```bash
lsof -i :9876
```

If not running, start it:
```bash
cd /Users/alex.macdonald/fix-connector/fix-server-go
python3 fix_server.py &
```

### Issue: "FIX connector not found"

**Rebuild and reinstall connector:**
```bash
cd /Users/alex.macdonald/fix-connector/mulesoft-fix-connector
mvn clean install -DskipTests
```

Then in Studio: Right-click project → Maven → Update Project

## 📁 Alternative: Command Line Deployment (Advanced)

If you prefer command line, here's the alternative:

### Set MULE_HOME
```bash
export MULE_HOME="/Applications/AnypointStudio.app/Contents/Eclipse/plugins/org.mule.tooling.server.4.10.ee_7.22.0.202510021413/mule"
```

### Package App
```bash
cd /Users/alex.macdonald/fix-connector/fix-sample-app
mvn clean package -DskipTests
```

### Deploy
```bash
cp target/fix-sample-app-1.0.0-mule-application.jar "$MULE_HOME/apps/"
```

### Start Mule
```bash
"$MULE_HOME/bin/mule" start
```

### View Logs
```bash
tail -f "$MULE_HOME/logs/mule_ee.log"
```

**However**, using Anypoint Studio is **much easier** and provides better debugging!

## 🎯 Summary

**Easiest Path:**
1. ✅ Open Anypoint Studio
2. ✅ Import `/Users/alex.macdonald/fix-connector/fix-sample-app`
3. ✅ Run As → Mule Application
4. ✅ Test with Postman collection

**Everything is ready:**
- ✅ FIX Server running on port 9876
- ✅ FIX Connector built and in Maven repo
- ✅ Sample app configured
- ✅ Postman collection ready
- ✅ Anypoint Studio installed

**Next Step:**
Open Anypoint Studio and import the project!

Let me know when you have it running, and we can test the Postman calls together!

