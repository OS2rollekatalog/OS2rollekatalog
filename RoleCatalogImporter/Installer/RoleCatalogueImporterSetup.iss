; This file is a script that allows to build the OrgSyncer instalation package
; To generate the installer, define the variable MyAppSourceDir MUST point to the Directory where the dll's should be copied from
; The script may be executed from the console-mode compiler - iscc "c:\isetup\samples\my script.iss" or from the Inno Setup Compiler UI
#define AppId "{{5008e124-582e-41cd-9ae0-edd6c0daddb6}"
#define AppSourceDir "\\VBOXSVR\brian\projects\role-catalogue\RoleCatalogImporter\RoleCatalogImporter\bin\Debug\"
#define AppName "RoleCatalogueImporter"
#define AppVersion "1.4.0"
#define AppPublisher "Digital Identity"
#define AppURL "http://digital-identity.dk/"
#define AppExeName "RoleCatalogueImporter.exe"

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
OutputBaseFilename=RoleCatalogueImporter
Compression=lzma
SolidCompression=yes
SourceDir={#AppSourceDir}
OutputDir=..\..\..\Installer
SetupIconFile={#AppSourceDir}\..\..\..\Installer\di.ico
UninstallDisplayIcon={#AppSourceDir}\..\..\..\Installer\di.ico

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "*.exe"; DestDir: "{app}"; Flags: "ignoreversion";
Source: "*.dll"; DestDir: "{app}"; Flags: "ignoreversion";
Source: "*.config"; DestDir: "{app}"; Flags: "ignoreversion onlyifdoesntexist";
Source: "*.pdb"; DestDir: "{app}"; Flags: "ignoreversion";

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; IconFilename: "{app}/di.ico";

[Run]
Filename: "{app}\RoleCatalogImporter.exe"; Parameters: "install"

[UninstallRun]
Filename: "{app}\RoleCatalogImporter.exe"; Parameters: "uninstall"
