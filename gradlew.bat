@echo off
setlocal
set DIR=%~dp0
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  echo Downloading official Gradle wrapper bootstrap...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/runelite/example-plugin/master/gradle/wrapper/gradle-wrapper.jar' -OutFile '%DIR%gradle\wrapper\gradle-wrapper.jar'"
  if errorlevel 1 exit /b 1
)
if defined JAVA_HOME (set JAVA_EXE=%JAVA_HOME%\bin\java.exe) else (set JAVA_EXE=java.exe)
"%JAVA_EXE%" -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
set "GRADLE_EXIT=%ERRORLEVEL%"
endlocal & exit /b %GRADLE_EXIT%
