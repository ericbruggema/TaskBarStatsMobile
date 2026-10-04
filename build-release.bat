@echo off
rem Ondertekende release-APK en AAB bouwen (vereist keystore.properties + .jks in deze map).
cd /d "%~dp0"
call gradlew.bat assembleRelease bundleRelease
if errorlevel 1 exit /b 1
echo.
echo APK: app\build\outputs\apk\release\app-release.apk
echo AAB: app\build\outputs\bundle\release\app-release.aab
