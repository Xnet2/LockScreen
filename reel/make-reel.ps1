# Renders the Reel: Reel.java draws each frame and pipes it into ffmpeg (H.264 MP4, 1080x1920, 30fps).
# Usage:  powershell -ExecutionPolicy Bypass -File reel\make-reel.ps1
$ErrorActionPreference = 'Continue'
$fso  = New-Object -ComObject Scripting.FileSystemObject
# Short 8.3 paths: the Windows user name is in Hebrew
$K    = $fso.GetFolder((Join-Path $env:LOCALAPPDATA 'VODOS-build')).ShortPath
$here = $fso.GetFolder($PSScriptRoot).ShortPath
$shots = $fso.GetFolder((Join-Path $PSScriptRoot '..\Screenshot')).ShortPath
$java = "$K\jdk-17.0.21+9\bin\java.exe"
$ff   = 'C:\Program Files\Tracktion\Waveform 13\ffmpeg.exe'
$tmp  = Join-Path $fso.GetFolder($env:TEMP).ShortPath 'lockscreen-reel'
New-Item -ItemType Directory -Force $tmp | Out-Null

& $java -Xmx2g "-Dfile.encoding=UTF-8" "$here\Reel.java" $shots "$tmp\reel.mp4" $ff "$tmp\cover.jpg"
if ($LASTEXITCODE -ne 0) { throw 'render failed' }
Copy-Item -Force "$tmp\reel.mp4" (Join-Path $PSScriptRoot 'LockScreen-Reel.mp4')
Copy-Item -Force "$tmp\cover.jpg" (Join-Path $PSScriptRoot 'cover.jpg')
Write-Host '== DONE'
