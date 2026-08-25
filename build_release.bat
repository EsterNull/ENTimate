@echo off
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-17
set _JAVA_OPTIONS=-Xmx4096m
set ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk
cd /d %~dp0

echo Building unsigned release APK...
call gradlew.bat :app:assembleRelease
if errorlevel 1 (
    echo BUILD FAILED
    exit /b 1
)

for /f "delims=" %%i in ('dir /b /ad /o-n "%ANDROID_SDK%\build-tools"') do (
    set BUILD_TOOLS=%ANDROID_SDK%\build-tools\%%i
    goto :found
)
:found

set IN=app\build\outputs\apk\release\app-release-unsigned.apk
set OUT=app\build\outputs\apk\release\ENTimate.apk
set KS=%USERPROFILE%\.android\debug.keystore

echo Signing to %OUT% using %BUILD_TOOLS%...
call "%BUILD_TOOLS%\apksigner.bat" sign ^
    --ks "%KS%" ^
    --ks-key-alias androiddebugkey ^
    --ks-pass pass:android ^
    --key-pass pass:android ^
    --out "%OUT%" "%IN%"
if errorlevel 1 (
    echo SIGNING FAILED
    exit /b 1
)

echo Done: %OUT%

set ADB=%ANDROID_SDK%\platform-tools\adb.exe
if exist "%ADB%" (
    echo Installing (reinstall, keeps app data)...
    "%ADB%" install -r "%OUT%" || echo Install skipped (device not connected?).
)

endlocal
