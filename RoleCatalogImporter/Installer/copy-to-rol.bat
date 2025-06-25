@echo off
setlocal

rem get the directory where the script is running
set "runDir=%~dp0"
rem remove trailing backslash from the directory path
set "runDir=%runDir:~0,-1%"

rem set destination directory (two folders up, then into ui\src\main\resources\static\download\roleCatalogueImporter)
set "destDir=%runDir%\..\..\ui\src\main\resources\static\download\roleCatalogueImporter"

rem create the destination directory if it doesn't exist
if not exist "%destDir%" (
    mkdir "%destDir%"
)

rem copy RoleCatalogueImporter.exe from the running directory to the destination directory
copy "%runDir%\RoleCatalogueImporter.exe" "%destDir%"

rem set source path for Changelog.txt (one folder up, then RoleCatalogImporter folder)
set "changelogSource=%runDir%\..\RoleCatalogImporter\Changelog.txt"

rem copy Changelog.txt to the destination directory
copy "%changelogSource%" "%destDir%"

endlocal
pause
