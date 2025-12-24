# FIX Connector Dependency Options for Mule App

You asked a great question about bundling the FIX connector with your Mule app. Here are **three working solutions**, with my recommendation:

## ✅ Option 1: Bundle JAR with Application (RECOMMENDED)

This bundles the connector directly in your application - perfect for portability!

### What I Just Did:
1. ✅ Copied connector JAR to `fix-sample-app/src/main/resources/lib/`
2. Now need to update POM to reference it

### Update Your POM:

Replace the current dependency section with this:

```xml
<dependencies>
    <!-- FIX Protocol Connector (bundled) -->
    <dependency>
        <groupId>com.fix.muleConnector</groupId>
        <artifactId>mulesoft-fix-connector</artifactId>
        <version>1.0.0</version>
        <scope>system</scope>
        <systemPath>${project.basedir}/src/main/resources/lib/mulesoft-fix-connector-1.0.0.jar</systemPath>
    </dependency>
    
    <!-- HTTP Connector -->
    <dependency>
        <groupId>org.mule.connectors</groupId>
        <artifactId>mule-http-connector</artifactId>
        <version>1.7.3</version>
        <classifier>mule-plugin</classifier>
    </dependency>
    
    <!-- Sockets Connector -->
    <dependency>
        <groupId>org.mule.connectors</groupId>
        <artifactId>mule-sockets-connector</artifactId>
        <version>1.2.3</version>
        <classifier>mule-plugin</classifier>
    </dependency>
</dependencies>
```

**Advantages:**
- ✅ App is self-contained and portable
- ✅ No need for local Maven repo
- ✅ Works in any environment (Studio, Runtime, CloudHub)
- ✅ Easy to share with teammates

**Disadvantages:**
- ⚠️ If you update the connector, you must copy the new JAR
- ⚠️ Maven warns about `system` scope (but it works fine)

---

## Option 2: Use Local Maven Repository (CURRENT)

Your current setup already uses this approach.

**Current POM:**
```xml
<dependency>
    <groupId>com.fix.muleConnector</groupId>
    <artifactId>mulesoft-fix-connector</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Advantages:**
- ✅ Standard Maven approach
- ✅ Easy to update (just `mvn install` in connector project)
- ✅ Works great in Anypoint Studio

**Disadvantages:**
- ❌ Requires connector in local `~/.m2/repository/`
- ❌ Won't work on other machines without installing connector
- ❌ CloudHub deployment requires publishing to Exchange

---

## Option 3: Publish to Anypoint Exchange (PRODUCTION)

For production and team sharing, publish to Exchange.

### Steps:

1. **Configure Exchange in connector POM:**
```xml
<distributionManagement>
    <repository>
        <id>exchange-repository</id>
        <name>Exchange Repository</name>
        <url>https://maven.anypoint.mulesoft.com/api/v3/organizations/{orgId}/maven</url>
    </repository>
</distributionManagement>
```

2. **Add credentials to `~/.m2/settings.xml`:**
```xml
<servers>
    <server>
        <id>exchange-repository</id>
        <username>~~~Client~~~</username>
        <password>{connected-app-client-secret}</password>
    </server>
</servers>
```

3. **Deploy:**
```bash
mvn deploy
```

4. **Reference in app:**
```xml
<dependency>
    <groupId>com.fix.muleConnector</groupId>
    <artifactId>mulesoft-fix-connector</artifactId>
    <version>1.0.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

**Advantages:**
- ✅ Professional, production-ready
- ✅ Team can access connector
- ✅ Version control
- ✅ Works with CloudHub

**Disadvantages:**
- ❌ Requires Anypoint Platform account
- ❌ Additional setup complexity

---

## 🎯 My Recommendation

**Use Option 1 (Bundle JAR)** for your current needs because:

1. You want to run the Mule app now
2. It's the most portable solution
3. Works everywhere (Studio, Runtime, CloudHub)
4. No external dependencies

Later, when you want to publish or share, you can move to Option 3 (Exchange).

---

## Quick Implementation of Option 1

I've already copied the JAR. Now just run this command to update your POM:

```bash
cd /Users/alex.macdonald/fix-connector/fix-sample-app
```

Then modify the `<dependencies>` section as shown above, and you're done!

---

## Alternative: Install as Embedded Library

Another approach is to use the mule-maven-plugin's `includeLibraries`:

```xml
<plugin>
    <groupId>org.mule.tools.maven</groupId>
    <artifactId>mule-maven-plugin</artifactId>
    <version>${mule.maven.plugin.version}</version>
    <extensions>true</extensions>
    <configuration>
        <sharedLibraries>
            <sharedLibrary>
                <groupId>com.fix.muleConnector</groupId>
                <artifactId>mulesoft-fix-connector</artifactId>
            </sharedLibrary>
        </sharedLibraries>
        <includeLibraries>
            <includeLibrary>com.fix.muleConnector:mulesoft-fix-connector</includeLibrary>
        </includeLibraries>
    </configuration>
</plugin>
```

This keeps your current dependency but ensures it's packaged with the app.

---

## Summary Table

| Option | Portability | Ease of Use | Production Ready | Setup Time |
|--------|-------------|-------------|------------------|------------|
| **Bundle JAR (Option 1)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 2 min |
| Local Maven (Option 2) | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | Already done |
| Exchange (Option 3) | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 30 min |

---

## Next Steps

Choose your preferred option and let me know if you want me to:
1. Update the POM for Option 1 (bundled JAR)
2. Keep current setup (Option 2) for Studio use
3. Help configure Exchange publishing (Option 3)

The JAR is ready at: `fix-sample-app/src/main/resources/lib/mulesoft-fix-connector-1.0.0.jar`

What would you like to do?

