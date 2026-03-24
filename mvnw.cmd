@echo off
@REM Maven Wrapper Start Script for Windows
@REM ----------------------------------------------------------------------------

@REM set %MAVEN_PROJECTBASEDIR% only when not already set
if NOT "%MAVEN_PROJECTBASEDIR%"=="" goto endBaseDir
set MAVEN_PROJECTBASEDIR=%~dp0

:endBaseDir
@REM ==== START VALIDATION ====
if NOT "%JAVA_HOME%"=="" goto OkJHome

@REM 检查常见Java安装路径
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin\java.exe" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if exist "C:\Program Files\Java\jdk-17.0.2\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.2"
if exist "D:\Program Files\Java\jdk-17\bin\java.exe" set "JAVA_HOME=D:\Program Files\Java\jdk-17"
if exist "E:\java\jdk-17\bin\java.exe" set "JAVA_HOME=E:\java\jdk-17"
if exist "%~dp0jdk-17.0.2\bin\java.exe" set "JAVA_HOME=%~dp0jdk-17.0.2"

if NOT "%JAVA_HOME%"=="" goto OkJHome
echo.
echo ERROR: JAVA_HOME is not set and no Java installation could be found.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
echo.
goto fail

:OkJHome
if exist "%JAVA_HOME%\bin\java.exe" goto chkMHome

echo.
echo ERROR: JAVA_HOME is set to an invalid directory.
echo JAVA_HOME = "%JAVA_HOME%"
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
echo.
goto fail

:chkMHome
set "MAVEN_HOME=%~dp0.mvn\wrapper"
set "MAVEN_CMD=%MAVEN_HOME%\maven-wrapper.jar"

if exist "%MAVEN_CMD%" goto runM2

echo.
echo ERROR: Maven wrapper not found.
echo.
goto fail

:runM2
set "MAVEN_CMD=%JAVA_HOME%\bin\java.exe -classpath %MAVEN_CMD% org.apache.maven.wrapper.MavenWrapperMain %MAVEN_CONFIG%"

@REM ==== RUN MAVEN ====
cd /d "%MAVEN_PROJECTBASEDIR%"
%MAVEN_CMD% %*
if ERRORLEVEL 1 goto fail
goto end

:fail
exit /b 1

:end
exit /b 0
