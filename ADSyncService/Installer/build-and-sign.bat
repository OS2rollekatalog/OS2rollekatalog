del ADSyncService.exe
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" ADSyncServiceSetup.iss
"signtool.exe" ^
  sign /td SHA256 /fd SHA256 ^
  /f ..\..\..\codesigning\codesigning.pfx /p Test1234 ^
  /tr http://timestamp.globalsign.com/tsa/r6advanced1 ^
  "ADSyncService.exe"

pause
