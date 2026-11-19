# Builds the APK without Gradle, using the kit in %LOCALAPPDATA%\VODOS-build
# Usage:
#   powershell -ExecutionPolicy Bypass -File build.ps1          -> LockScreen.apk       (protection via accessibility)
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Root    -> LockScreen-Root.apk  (protection via root, no accessibility)
param([switch]$Root)
$ErrorActionPreference = 'Continue' # native tools write warnings to stderr; we check exit codes

$VERSION_CODE = 8
$VERSION_NAME = '1.7'

# Short 8.3 path: the Windows user name is in Hebrew and aapt2 can't open non-ASCII paths
$K    = (New-Object -ComObject Scripting.FileSystemObject).GetFolder((Join-Path $env:LOCALAPPDATA 'VODOS-build')).ShortPath
$JDK  = "$K\jdk-17.0.21+9\bin"
$AJAR = "$K\sdk\pf\android-34\android.jar"
$BT   = "$K\sdk\bt\android-14"
$OUT  = "$K\lockscreen-out"
$KS   = "$K\lockscreen.keystore"
$PASS = 'lockscreen123'
$NAME = if ($Root) { 'LockScreen-Root.apk' } else { 'LockScreen.apk' }

function Run($exe, [string[]]$a) {
    & $exe @a
    if ($LASTEXITCODE -ne 0) { throw "FAILED: $exe" }
}
$utf8 = New-Object Text.UTF8Encoding $false

if (Test-Path $OUT) { Remove-Item -Recurse -Force $OUT }
New-Item -ItemType Directory -Force "$OUT\gen", "$OUT\classes", "$OUT\dex" | Out-Null

# Stage sources next to the tools (external tools can't open the project folder path)
Copy-Item -Recurse (Join-Path $PSScriptRoot 'app\src\main') "$OUT\src"
$SRC = "$OUT\src"

# Manifest with package attribute (aapt2 needs it; Gradle uses `namespace` instead)
$m = Get-Content "$SRC\AndroidManifest.xml" -Raw -Encoding UTF8
$m = $m.Replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
    "<manifest xmlns:android=`"http://schemas.android.com/apk/res/android`" package=`"com.custom.lockscreen`" android:versionCode=`"$VERSION_CODE`" android:versionName=`"$VERSION_NAME`">")

if ($Root) {
    Write-Host '== Root variant'
    # No accessibility service in the root build
    $m = [regex]::Replace($m, '(?s)\s*<service\s+android:name="\.LockGuardService".*?</service>', '')
    $m = $m.Replace('android:label="@string/app_name"', 'android:label="@string/app_name_root"')
    $flavor = "$SRC\java\com\custom\lockscreen\Flavor.java"
    $f = [IO.File]::ReadAllText($flavor).Replace('static final boolean ROOT = false;', 'static final boolean ROOT = true;')
    [IO.File]::WriteAllText($flavor, $f, $utf8)
}
[IO.File]::WriteAllText("$OUT\AndroidManifest.xml", $m, $utf8)

Write-Host '== aapt2'
Run "$BT\aapt2.exe" @('compile', '--dir', "$SRC\res", '-o', "$OUT\res.zip")
Run "$BT\aapt2.exe" @('link', '-I', $AJAR, '--manifest', "$OUT\AndroidManifest.xml",
    '--min-sdk-version', '31', '--target-sdk-version', '32',
    '--java', "$OUT\gen", '-o', "$OUT\base.apk", "$OUT\res.zip")

Write-Host '== javac'
$java = @(Get-ChildItem "$SRC\java", "$OUT\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Run "$JDK\javac.exe" (@('-encoding', 'UTF-8', '-Xlint:-options', '-source', '17', '-target', '17',
    '-cp', $AJAR, '-d', "$OUT\classes") + $java)

Write-Host '== d8'
$classes = @(Get-ChildItem "$OUT\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
Run "$JDK\java.exe" (@('-cp', "$BT\lib\d8.jar", 'com.android.tools.r8.D8', '--release', '--min-api', '31',
    '--lib', $AJAR, '--output', "$OUT\dex") + $classes)

Write-Host '== package'
Push-Location "$OUT\dex"
Run "$JDK\jar.exe" @('-uf', "$OUT\base.apk", 'classes.dex')
Pop-Location
Run "$BT\zipalign.exe" @('-f', '-p', '4', "$OUT\base.apk", "$OUT\aligned.apk")

if (-not (Test-Path $KS)) {
    Run "$JDK\keytool.exe" @('-genkeypair', '-keystore', $KS, '-alias', 'lockscreen', '-keyalg', 'RSA',
        '-keysize', '2048', '-validity', '10000', '-storepass', $PASS, '-keypass', $PASS,
        '-dname', 'CN=LockScreen')
}
Run "$JDK\java.exe" @('-jar', "$BT\lib\apksigner.jar", 'sign', '--ks', $KS, '--ks-pass', "pass:$PASS",
    '--out', "$OUT\$NAME", "$OUT\aligned.apk")
Copy-Item -Force "$OUT\$NAME" (Join-Path $PSScriptRoot $NAME)
Write-Host "== DONE: $NAME"
