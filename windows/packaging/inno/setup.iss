; Inno Setup Script for Glypdl (Windows 11 x64)

#define MyAppName "Glypdl"
#ifndef MyAppVersion
#define MyAppVersion "1.2.0"
#endif
#define MyAppPublisher "Suresh Soundararajan"
#define MyAppURL "https://github.com/sureshsoudararajan/Glypdl"
#define MyAppExeName "Glypdl.Windows.exe"
#define MyAppId "{D8E5F920-56B4-4B28-89E2-7D7E95C3C8F1}"

[Setup]
AppId={{D8E5F920-56B4-4B28-89E2-7D7E95C3C8F1}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}/issues
AppUpdatesURL={#MyAppURL}/releases
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=commandline
AllowNoIcons=yes
LicenseFile=..\..\..\LICENSE
OutputDir=..\..\dist
OutputBaseFilename=Glypdl-{#MyAppVersion}-Setup-x64
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=app.ico
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\..\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon; IconFilename: "{app}\{#MyAppExeName}"

[UninstallDelete]
Type: filesandordirs; Name: "{app}"
Type: filesandordirs; Name: "{localappdata}\Glypdl"

[Run]
Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
function GetInstalledVersion(): String;
var
  InstalledVersion: String;
begin
  InstalledVersion := '';
  if RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'DisplayVersion', InstalledVersion) then
  begin
    Result := InstalledVersion;
    Exit;
  end;
  if RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'DisplayVersion', InstalledVersion) then
  begin
    Result := InstalledVersion;
    Exit;
  end;
  Result := '';
end;

function IsAppAlreadyInstalled(): Boolean;
var
  UninstallStr: String;
  InstallLoc: String;
  ExePath: String;
begin
  Result := False;
  if RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'UninstallString', UninstallStr) then
  begin
    if (UninstallStr <> '') and RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'InstallLocation', InstallLoc) then
    begin
      if DirExists(InstallLoc) then
      begin
        Result := True;
        Exit;
      end;
    end
    else if UninstallStr <> '' then
    begin
      Result := True;
      Exit;
    end;
  end;

  if RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'UninstallString', UninstallStr) then
  begin
    if (UninstallStr <> '') and RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'InstallLocation', InstallLoc) then
    begin
      if DirExists(InstallLoc) then
      begin
        Result := True;
        Exit;
      end;
    end
    else if UninstallStr <> '' then
    begin
      Result := True;
      Exit;
    end;
  end;

  ExePath := ExpandConstant('{localappdata}\Programs\{#MyAppName}\{#MyAppExeName}');
  if FileExists(ExePath) then
  begin
    Result := True;
    Exit;
  end;
end;

function InitializeSetup(): Boolean;
var
  PromptMsg: String;
  PrevVer: String;
begin
  Result := True;
  if IsAppAlreadyInstalled() then
  begin
    PrevVer := GetInstalledVersion();
    if PrevVer <> '' then
      PromptMsg := 'Glypdl (version ' + PrevVer + ') is already installed on your computer.' + #13#10#13#10 + 'Do you want to reinstall or update it?'
    else
      PromptMsg := 'Glypdl is already installed on your computer.' + #13#10#13#10 + 'Do you want to reinstall or update it?';

    if MsgBox(PromptMsg, mbConfirmation, MB_YESNO or MB_DEFBUTTON2) <> IDYES then
    begin
      Result := False;
      Exit;
    end;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  DataDir: String;
  AppDir: String;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    // Remove auto-downloaded dependencies (yt-dlp.exe, ffmpeg.exe), database, cookies, settings, and logs
    DataDir := ExpandConstant('{localappdata}\Glypdl');
    if DirExists(DataDir) then
    begin
      DelTree(DataDir, True, True, True);
    end;

    // Ensure entire application installation folder is removed
    AppDir := ExpandConstant('{app}');
    if DirExists(AppDir) then
    begin
      DelTree(AppDir, True, True, True);
    end;
  end;
end;
