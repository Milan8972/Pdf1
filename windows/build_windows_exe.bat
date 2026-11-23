@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Installing Python dependencies...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
if errorlevel 1 exit /b 1

echo [2/4] Generating paperpilot.ico...
python make_icon.py
if errorlevel 1 exit /b 1

echo [3/4] Building standalone paperpilot.exe with PyInstaller...
python -m PyInstaller --noconfirm --clean --onefile --windowed ^
  --name paperpilot ^
  --icon paperpilot.ico ^
  --collect-all customtkinter ^
  --collect-all fitz ^
  paperpilot_app.py
if errorlevel 1 exit /b 1

echo [4/4] Build complete!
echo Executable created at: %cd%\dist\paperpilot.exe
endlocal
