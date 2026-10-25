@echo off
rem prlens review ^<PR URL^> [--format markdown^|json] [--config ^<path^>]
rem Build the jar first with gradlew.bat bootJar (backend\build\libs\prlens.jar).
rem If non-ASCII output is garbled in cmd, run chcp 65001 first.
java -jar "%~dp0..\build\libs\prlens.jar" %*
exit /b %ERRORLEVEL%
