@echo off
mode con cols=40 lines=15
:: Get ADMIN Privs
:-------------------------------------
mkdir "%windir%\BatchGotAdmin"
if '%errorlevel%' == '0' (
  rmdir "%windir%\BatchGotAdmin" & goto gotAdmin 
) else ( goto UACPrompt )

:UACPrompt
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    echo UAC.ShellExecute %0, "", "", "runas", 1 >> "%temp%\getadmin.vbs"

    "%temp%\getadmin.vbs"

    exit /B

:gotAdmin
    if exist "%temp%\getadmin.vbs" ( del "%temp%\getadmin.vbs" )
    pushd "%CD%"      
    CD /D "%~dp0"

:-------------------------------------
:: End Get ADMIN Privs

@echo off
mode con cols=40 lines=20
%SystemRoot%\System32\Reg.exe add "HKCR\Directory\Background\shell\KitToolShell\command" /ve /t REG_SZ /d "\"%SystemRoot%\System32\cmd.exe\" /c \"\"%~dp0start.bat\" \"%%V\"\"\"" /f
PAUSE
EXIT