; This file is a script that allows to build the OrgSyncer instalation package
; To generate the installer, define the variable MyAppSourceDir MUST point to the Directory where the dll's should be copied from
; The script may be executed from the console-mode compiler - iscc "c:\isetup\samples\my script.iss" or from the Inno Setup Compiler UI
#define AppId "{{d438b389-0f50-4b9e-8311-6e9f81774587}"
#define AppSourceDir "..\ADSyncService\bin\Debug\"
#define AppName "ADSyncService"
#define AppVersion "2.3.0"
#define AppPublisher "Digital Identity"
#define AppURL "http://digital-identity.dk/"
#define AppExeName "ADSyncService.exe"

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
DefaultDirName={pf}\{#AppPublisher}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
SetupLogging=yes
OutputBaseFilename=ADSyncService
Compression=lzma
SolidCompression=yes
SourceDir={#AppSourceDir}
OutputDir=..\..\..\Installer

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "*.exe"; DestDir: "{app}"; Flags: "ignoreversion";
Source: "*.txt"; DestDir: "{app}"; Flags: "ignoreversion";
Source: "*.dll"; DestDir: "{app}"; Flags: "ignoreversion";
Source: "*.pdb"; DestDir: "{app}"; Flags: "ignoreversion";
Source: "Log.config"; DestDir: "{app}"; Flags: "ignoreversion onlyifdoesntexist";
Source: "ADSyncService.exe.config"; DestDir: "{app}"; Flags: "ignoreversion onlyifdoesntexist";

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; IconFilename: "{app}/di.ico";

[Run]
Filename: "{app}\ADSyncService.exe"; Parameters: "install"

[UninstallRun]
Filename: "{app}\ADSyncService.exe"; Parameters: "uninstall"
