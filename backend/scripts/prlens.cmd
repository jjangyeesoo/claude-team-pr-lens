@echo off
rem prlens review ^<PR URL^> [--format markdown^|json] [--config ^<경로^>]
rem 실행 jar는 `gradlew.bat bootJar`로 만든다 (backend\build\libs\prlens.jar).
rem cmd에서 한글이 깨지면 먼저 `chcp 65001`을 실행한다.
java -jar "%~dp0..\build\libs\prlens.jar" %*
exit /b %ERRORLEVEL%
