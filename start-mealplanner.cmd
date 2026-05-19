@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo Starting Meal Planner with embedded Tomcat...
call .\mvnw.cmd spring-boot:run

endlocal
