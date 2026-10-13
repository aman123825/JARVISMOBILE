@echo off
echo ========================================================
echo        MindFortress - Distraction Purge Script
echo ========================================================
echo.
echo This script removes pre-installed games, adware, and distracting apps
echo from your phone for User 0 (without requiring Root).
echo.
set ADB_PATH="C:\Users\vivek\AppData\Local\Android\Sdk\platform-tools\adb.exe"

%ADB_PATH% devices
echo.
set /p CONFIRM="Are you ready to purge distracting apps and games from your phone? (Y/N): "
if /i not "%CONFIRM%"=="Y" exit /b 0

echo.
echo Purging distracting social and game packages...

:: List of distracting apps & games to purge
for %%P in (
    com.zhiliaoapp.musically
    com.ss.android.ugc.trill
    com.instagram.android
    com.facebook.katana
    com.facebook.orca
    com.snapchat.android
    com.reddit.frontpage
    com.twitter.android
    com.roblox.client
    com.king.candycrushsaga
    com.kiloo.subwaysurf
    com.dts.freefireth
    com.dts.freefiremax
    com.pubg.imobile
    com.tencent.ig
    com.supercell.clashofclans
    com.supercell.brawlstars
    com.miniclip.eightballpool
    com.scopely.monopolygo
    com.playrix.gardenscapes
    com.playrix.homescapes
) do (
    echo Uninstalling %%P...
    %ADB_PATH% shell pm uninstall -k --user 0 %%P
)

echo.
echo ========================================================
echo Purge complete! Only high-signal utilities and MindFortress remain.
echo ========================================================
pause
