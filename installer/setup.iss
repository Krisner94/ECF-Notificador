[Setup]
AppName=ECF Notificador
AppVersion=1.0.0
AppPublisher=Krisner94
DefaultDirName={pf}\ECF Notificador
DefaultGroupName=ECF Notificador
OutputDir=output
OutputBaseFilename=ecf-notificador-setup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
SetupIconFile=files\EcfNotificador.ico
UninstallDisplayIcon={app}\EcfNotificador.ico

[Languages]
Name: "portuguese"; MessagesFile: "compiler:Languages\Portuguese.isl"

[Tasks]
Name: "desktopicon"; Description: "Criar atalho na área de trabalho"; GroupDescription: "Opções adicionais:"; Flags: unchecked
Name: "startup"; Description: "Iniciar com o Windows"; GroupDescription: "Opções adicionais:"; Flags: checkedonce

[Dirs]
Name: "{app}\config"

[Files]
Source: "files\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs
Source: "files\config\*"; DestDir: "{app}\config"; Flags: ignoreversion recursesubdirs uninsneveruninstall

[Icons]
Name: "{group}\ECF Notificador"; Filename: "{app}\ECF-Notificador.exe"
Name: "{commondesktop}\ECF Notificador"; Filename: "{app}\ECF-Notificador.exe"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; \
ValueType: string; ValueName: "ECFNotificador"; ValueData: """{app}\ECF-Notificador.exe"""; \
Tasks: startup

Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; \
ValueType: string; ValueName: "ECFNotificador"; Flags: uninsdeletevalue

[Run]
Filename: "taskkill"; Parameters: "/IM ECF-Notificador.exe /F"; Flags: runhidden
Filename: "{app}\ECF-Notificador.exe"; Description: "Executar após instalação"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "taskkill"; Parameters: "/IM ECF-Notificador.exe /F"; Flags: runhidden