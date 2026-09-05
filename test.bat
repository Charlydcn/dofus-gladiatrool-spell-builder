@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
java "-Dfile.encoding=UTF-8" -jar target\dofus-gladiatrool-spell-builder.jar --self-test
if errorlevel 1 exit /b 1
echo Self-test OK
