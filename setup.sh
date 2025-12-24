#!/bin/bash

# Setup script for FIX Protocol Connector Testing

echo "========================================"
echo "FIX Protocol Connector - Setup Script"
echo "========================================"
echo ""

# Check Java
echo "Checking Java..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo "✓ Java is installed: $JAVA_VERSION"
else
    echo "✗ Java is not installed"
    echo "  Please install Java 17 or higher"
    exit 1
fi

# Check Maven
echo "Checking Maven..."
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -n 1)
    echo "✓ Maven is installed: $MVN_VERSION"
else
    echo "✗ Maven is not installed"
    echo "  Please install Maven 3.6 or higher"
    exit 1
fi

# Check Go
echo "Checking Go..."
if command -v go &> /dev/null; then
    GO_VERSION=$(go version)
    echo "✓ Go is installed: $GO_VERSION"
    GO_INSTALLED=true
else
    echo "✗ Go is not installed"
    echo ""
    echo "To install Go:"
    echo ""
    echo "macOS:"
    echo "  brew install go"
    echo ""
    echo "Linux:"
    echo "  wget https://go.dev/dl/go1.21.5.linux-amd64.tar.gz"
    echo "  sudo tar -C /usr/local -xzf go1.21.5.linux-amd64.tar.gz"
    echo "  export PATH=\$PATH:/usr/local/go/bin"
    echo ""
    echo "Or download from: https://go.dev/dl/"
    echo ""
    GO_INSTALLED=false
fi

echo ""
echo "========================================"
echo "Building Components"
echo "========================================"
echo ""

# Build FIX Connector
echo "Building FIX Connector..."
cd mulesoft-fix-connector
if mvn clean install -q; then
    echo "✓ FIX Connector built successfully"
else
    echo "✗ Failed to build FIX Connector"
    exit 1
fi
cd ..

# Build Go Server (if Go is installed)
if [ "$GO_INSTALLED" = true ]; then
    echo "Building FIX Server (Go)..."
    cd fix-server-go
    if go build -o fix-server; then
        echo "✓ FIX Server built successfully"
    else
        echo "✗ Failed to build FIX Server"
        exit 1
    fi
    cd ..
else
    echo "⚠ Skipping FIX Server build (Go not installed)"
fi

echo ""
echo "========================================"
echo "Setup Complete!"
echo "========================================"
echo ""

if [ "$GO_INSTALLED" = true ]; then
    echo "To start testing:"
    echo ""
    echo "Terminal 1 - Start FIX Server:"
    echo "  cd fix-server-go && ./fix-server"
    echo ""
    echo "Terminal 2 - Start MuleSoft App:"
    echo "  cd fix-sample-app && mvn mule:run"
    echo ""
    echo "Terminal 3 - Run Tests:"
    echo "  ./test-connector.sh"
else
    echo "Please install Go first, then run this script again."
    echo ""
    echo "After installing Go, you can:"
    echo "  1. Run: ./setup.sh"
    echo "  2. Follow the testing instructions in TESTING_GUIDE.md"
fi

echo ""

