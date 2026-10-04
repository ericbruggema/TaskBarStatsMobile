@echo off
rem Start de Android-emulator "tsphone" (android-35, x86_64) en wacht tot hij klaar is.
rem Optioneel: "start-emulator.bat install" bouwt de debug-APK en installeert hem daarna.
setlocal
set "SDK=%LOCALAPPDATA%\Android\Sdk"
if exist "%~dp0local.properties" for /f "tokens=2 delims==" %%a in ('findstr /b sdk.dir "%~dp0local.properties"') do set "SDK=%%a"
set "SDK=%SDK:\\=\%"
set "PATH=%SDK%\platform-tools;%SDK%\emulator;%PATH%"
if not exist "%SDK%\emulator\emulator.exe" ( echo Emulator niet gevonden in %SDK% & exit /b 1 )
adb start-server >nul 2>&1
adb devices | findstr /r "emulator-.*device$" >nul
if errorlevel 1 (
  echo Emulator starten...
  start "" "%SDK%\emulator\emulator.exe" -avd tsphone -no-snapshot-save
)
echo Wachten tot Android klaar is...
adb wait-for-device
:boot
adb shell getprop sys.boot_completed | findstr 1 >nul || ( ping -n 3 127.0.0.1 >nul & goto boot )
echo Emulator klaar.
if /i "%~1"=="install" (
  if not defined JAVA_HOME for /d %%j in ("%ProgramFiles%\Microsoft\jdk-17*") do set "JAVA_HOME=%%j"
  pushd "%~dp0" & call gradlew.bat assembleDebug & popd
  adb install -r "%~dp0app\build\outputs\apk\debug\app-debug.apk"
  adb shell am start -n com.ericbruggema.taskbarstatsmobile/com.ericbruggema.taskbarstats.MainActivity
)
