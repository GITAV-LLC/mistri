; mistri by GITAV — Windows installer (NSIS 3, Modern UI 2)
; Built by installer/build.sh, which passes VERSION, STAGE, ROOT and OUTFILE.

Unicode true
SetCompressor /SOLID lzma
SetCompressorDictSize 64
ManifestDPIAware true
RequestExecutionLevel admin

; Code signing: build.sh defines SIGN when signing credentials are present (release builds).
; The uninstaller is signed as it is written into the installer, then the installer itself.
!ifdef SIGN
  !uninstfinalize '"${SIGN}" "%1"' = 0
  !finalize '"${SIGN}" "%1"' = 0
!endif

!define APP      "mistri"
!define PUB      "GITAV"
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\mistri"
!define AUMID    "GITAV.mistri"

Name "${APP} by ${PUB}"
Caption "${APP} ${VERSION} Setup"
BrandingText "${APP} by ${PUB}"
OutFile "${OUTFILE}"
InstallDir "$PROGRAMFILES64\${APP}"
InstallDirRegKey HKLM "${UNINSTKEY}" "InstallLocation"
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APP}"
VIAddVersionKey "CompanyName" "${PUB}"
VIAddVersionKey "FileDescription" "${APP} Setup"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "${PUB}"

!include "MUI2.nsh"
!include "x64.nsh"
!include "LogicLib.nsh"
!include "WinVer.nsh"

!define MUI_ICON "${ROOT}\mistri\assets\mistri.ico"
!define MUI_UNICON "${ROOT}\mistri\assets\mistri.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${ROOT}\branding\installer-side.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "${ROOT}\branding\installer-side.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP "${ROOT}\branding\installer-header.bmp"
!define MUI_ABORTWARNING
!define MUI_COMPONENTSPAGE_SMALLDESC

!define MUI_WELCOMEPAGE_TITLE "Welcome to mistri"
!define MUI_WELCOMEPAGE_TEXT "mistri (مستری) keeps your PC in top shape: junk cleanup, malware scans, Windows repair, and control over startup apps, services and processes.$\r$\n$\r$\nIt never runs in the background and changes nothing until you press a button.$\r$\n$\r$\nClick Next to continue."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_TITLE "mistri is ready"
!define MUI_FINISHPAGE_TEXT "mistri has been installed. Find it in the Start menu.$\r$\n$\r$\nThe Chrome extension (mistri Shield) can be set up from mistri's Security page."
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Open mistri now"
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchApp
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

; ------------------------------------------------------------------ helpers

; Set the "Run as administrator" flag on a shortcut (byte 0x15, bit 0x20).
!macro RunAsAdmin LNK
  ClearErrors
  FileOpen $9 "${LNK}" a
  ${IfNot} ${Errors}
    FileSeek $9 0x15 SET
    FileReadByte $9 $8
    IntOp $8 $8 | 0x20
    FileSeek $9 0x15 SET
    FileWriteByte $9 $8
    FileClose $9
  ${EndIf}
!macroend

Function LaunchApp
  ; Installer is elevated already, so the app opens with full rights and no second prompt.
  Exec '"$INSTDIR\runtime\pythonw.exe" -m mistri'
FunctionEnd

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "mistri needs 64-bit Windows 10 or 11."
    Abort
  ${EndIf}
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_ICONSTOP "mistri needs Windows 10 or 11."
    Abort
  ${EndIf}
  SetRegView 64
FunctionEnd

; ------------------------------------------------------------------ sections

Section "mistri (required)" SecCore
  SectionIn RO
  SetShellVarContext all
  ; Clean upgrade: replace program files, keep user data in %LOCALAPPDATA%\mistri
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"

  SetOutPath "$INSTDIR"
  File "${STAGE}\mistri.ico"
  File "${STAGE}\mistri.cmd"
  File "${STAGE}\README.md"
  File "${STAGE}\THIRD-PARTY-NOTICES.txt"
  File "${STAGE}\PYTHON-LICENSE.txt"
  File /r "${STAGE}\runtime"
  File /r "${STAGE}\app"

  WriteUninstaller "$INSTDIR\uninstall.exe"
  WriteRegStr HKLM "${UNINSTKEY}" "DisplayName" "${APP}"
  WriteRegStr HKLM "${UNINSTKEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKLM "${UNINSTKEY}" "Publisher" "${PUB}"
  WriteRegStr HKLM "${UNINSTKEY}" "DisplayIcon" "$INSTDIR\mistri.ico"
  WriteRegStr HKLM "${UNINSTKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "${UNINSTKEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKLM "${UNINSTKEY}" "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
  WriteRegDWORD HKLM "${UNINSTKEY}" "NoModify" 1
  WriteRegDWORD HKLM "${UNINSTKEY}" "NoRepair" 1
  WriteRegDWORD HKLM "${UNINSTKEY}" "EstimatedSize" 60000

  ; Tidy up the earlier "PCMaint" test version, if present (current user's shortcuts + task)
  SetShellVarContext current
  Delete "$SMPROGRAMS\PCMaint.lnk"
  Delete "$DESKTOP\PCMaint.lnk"
  nsExec::Exec 'schtasks /Delete /TN "PCMaint Weekly Clean" /F'
  Pop $0
  SetShellVarContext all
SectionEnd

Section "Start menu shortcut" SecStart
  SetShellVarContext all
  CreateShortCut "$SMPROGRAMS\mistri.lnk" "$INSTDIR\runtime\pythonw.exe" "-m mistri" "$INSTDIR\mistri.ico" 0 \
      SW_SHOWNORMAL "" "mistri by GITAV: keep your PC in top shape"
  !insertmacro RunAsAdmin "$SMPROGRAMS\mistri.lnk"
SectionEnd

Section "Desktop shortcut" SecDesktop
  SetShellVarContext all
  CreateShortCut "$DESKTOP\mistri.lnk" "$INSTDIR\runtime\pythonw.exe" "-m mistri" "$INSTDIR\mistri.ico" 0 \
      SW_SHOWNORMAL "" "mistri by GITAV: keep your PC in top shape"
  !insertmacro RunAsAdmin "$DESKTOP\mistri.lnk"
SectionEnd

Section "ClamAV scanner (about 500 MB download)" SecClam
  DetailPrint "Setting up ClamAV. The download resumes automatically if the connection stalls."
  nsExec::ExecToLog '"$INSTDIR\runtime\python.exe" -m mistri setup-clamav'
  Pop $0
  ${If} $0 != 0
    DetailPrint "ClamAV wasn't set up (code $0). You can install it later from mistri's Security page."
  ${EndIf}
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecCore} "The mistri app with its own private Python runtime. Nothing else on your PC is changed."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecStart} "Add mistri to the Start menu."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDesktop} "Add a mistri icon to the desktop."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecClam} "Open-source second-opinion malware scanner from Cisco. Downloads Cisco's signed installer and its signatures. Skip it now and add it later if you like."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ------------------------------------------------------------------ uninstall

Function un.onInit
  SetRegView 64
FunctionEnd

Section "Uninstall"
  SetShellVarContext all
  nsExec::Exec 'schtasks /Delete /TN "mistri Weekly Clean" /F'
  Pop $0
  Delete "$SMPROGRAMS\mistri.lnk"
  Delete "$DESKTOP\mistri.lnk"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\mistri.ico"
  Delete "$INSTDIR\mistri.cmd"
  Delete "$INSTDIR\README.md"
  Delete "$INSTDIR\THIRD-PARTY-NOTICES.txt"
  Delete "$INSTDIR\PYTHON-LICENSE.txt"
  Delete "$INSTDIR\uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKLM "${UNINSTKEY}"

  SetShellVarContext current
  ${IfNot} ${Silent}
    MessageBox MB_YESNO|MB_ICONQUESTION "Also remove mistri's settings, logs and quarantine folder?$\r$\n$\r$\n($LOCALAPPDATA\mistri)" IDNO keep
      RMDir /r "$LOCALAPPDATA\mistri"
    keep:
  ${EndIf}
  DetailPrint "ClamAV, if installed, stays on your PC. Remove it from Settings > Apps if you no longer want it."
SectionEnd
