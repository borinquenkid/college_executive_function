#!/bin/bash

# CEF Local Verification Script
# This script automates the process of checking project stability across JVM, Android, and iOS.

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "--------------------------------------------------"
echo "Starting College Executive Function Verification"
echo "--------------------------------------------------"

# 1. JVM Tests (Desktop)
echo -e "\n[1/4] Running JVM Tests..."
./gradlew :composeApp:jvmTest
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ JVM Tests Passed${NC}"
else
    echo -e "${RED}✗ JVM Tests Failed${NC}"
    exit 1
fi

# 2. Android Unit Tests
echo -e "\n[2/4] Running Android Unit Tests..."
./gradlew :composeApp:testDebugUnitTest
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Android Unit Tests Passed${NC}"
else
    echo -e "${RED}✗ Android Unit Tests Failed${NC}"
    exit 1
fi

# 3. Android Build Check (Compilation)
echo -e "\n[3/4] Building Android APK (Dry Run)..."
./gradlew :composeApp:assembleDebug
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Android Compilation Success${NC}"
else
    echo -e "${RED}✗ Android Compilation Failed${NC}"
    exit 1
fi

# 4. iOS Framework Check (Compilation)
echo -e "\n[4/4] Verifying iOS Compilation (Universal Framework)..."
# This checks if the KMP shared code and iOS specific code compiles correctly for the simulator
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ iOS Compilation Success${NC}"
else
    echo -e "${RED}✗ iOS Compilation Failed${NC}"
    exit 1
fi

echo -e "\n--------------------------------------------------"
echo -e "${GREEN}ALL PLATFORMS VERIFIED SUCCESSFULLY${NC}"
echo "--------------------------------------------------"
