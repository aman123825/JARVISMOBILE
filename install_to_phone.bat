@echo off
echo ========================================================
echo        MindFortress - Autonomous Jarvis Guardian
echo         Samsung Galaxy S25 Zero-Touch Installer
echo ========================================================
echo.
echo Checking for connected Android phone via ADB...
echo.

set ADB_PATH="C:\Users\vivek\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set APK_PATH="C:\Users\vivek\.gemini\antigravity\scratch\mindfortress\app\build\outputs\apk\debug\app-debug.apk"

if not exist %ADB_PATH% (
    echo Error: ADB not found at %ADB_PATH%
    pause
    exit /b 1
)

%ADB_PATH% devices
echo.
echo If your phone is connected and USB debugging is enabled, installing MindFortress now...
echo.

%ADB_PATH% install -r %APK_PATH%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo SUCCESS: MindFortress installed on your mobile phone!
    echo ========================================================
    echo.
    echo Configuring Autonomous Background Permissions via ADB:
    echo 1. Whitelisting from Samsung OneUI battery optimization...
    %ADB_PATH% shell dumpsys deviceidle whitelist +com.mindfortress.app
    %ADB_PATH% shell appops set com.mindfortress.app RUN_IN_BACKGROUND allow

    echo 2. Granting Microphone and Notification permissions...
    %ADB_PATH% shell pm grant com.mindfortress.app android.permission.RECORD_AUDIO
    %ADB_PATH% shell pm grant com.mindfortress.app android.permission.POST_NOTIFICATIONS

    echo 3. Activating Distraction & Game Blocker Accessibility Service...
    %ADB_PATH% shell settings put secure enabled_accessibility_services com.mindfortress.app/com.mindfortress.app.service.AppBlockerAccessibilityService
    %ADB_PATH% shell settings put secure accessibility_enabled 1

    echo 4. Launching Autonomous Invisible Situational Guardian...
    %ADB_PATH% shell am start-foreground-service com.mindfortress.app/.service.SituationalGuardianService
    echo.
    echo ========================================================
    echo GUARDIAN FULLY ARMED AND ACTIVE IN BACKGROUND!
    echo - Background Situational Guardian: Active
    echo - Invisible Voice Intervention: Active (zero screen popups)
    echo - Distraction & Game Blocker: Active
    echo - Late-Night & Compulsive Unlock Senses: Active
    echo ========================================================
) else (
    echo.
    echo Installation failed or no device detected.
    echo Ensure your phone is connected via USB with "USB Debugging" turned ON in Developer Options.
    echo Alternatively, copy 'app-debug.apk' directly to your phone storage and tap to install.
)

pause
