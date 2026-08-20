@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

where java >nul 2>&1 || (echo ERREUR: Java 11+ introuvable dans le PATH & exit /b 1)

if not exist mvnw.cmd (
    echo ERREUR: Maven Wrapper introuvable: mvnw.cmd
    exit /b 1
)

call mvnw.cmd --no-transfer-progress clean package
if errorlevel 1 exit /b 1

echo Build OK: target\dofus-gladiatrool-spell-builder.jar
exit /b 0
