@echo off
rem Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
set ARGS=%*

if "%ARGS%" == "" (
    gradlew.bat run    
) else (
	gradlew.bat run --args="%ARGS%"
)
