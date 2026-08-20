@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

if not exist builder.properties (
    copy /y builder.properties.example builder.properties >nul
    echo Configuration créée: builder.properties
    echo Modifiez les chemins de ce fichier puis relancez run.bat.
    echo.
    pause
    exit /b 1
)

if not exist target\dofus-gladiatrool-spell-builder.jar call build.bat
if errorlevel 1 exit /b 1

java -Dfile.encoding=UTF-8 -jar target\dofus-gladiatrool-spell-builder.jar

echo.
pause
