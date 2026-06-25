@echo off
title MailBridge

cd /d "%~dp0\target"

java -jar mailbridge-1.0-SNAPSHOT-shaded.jar

pause