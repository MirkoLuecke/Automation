@echo off
rem Run from the com.example.automation.parent directory after "mvn clean install".
rem Updates local-repo/p2/automation-plugin/ with the freshly built update site.

set SRC=%~dp0..\com.example.automation.site\target\repository
set DST=%~dp0p2\automation-plugin

if not exist "%SRC%\p2.index" (
    echo ERROR: %SRC% not found. Run "mvn clean install" first.
    exit /b 1
)

if exist "%DST%" rmdir /s /q "%DST%"
xcopy /e /i /q "%SRC%" "%DST%"
echo Automation plugin site updated in local-repo\p2\automation-plugin\
