param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [string]$SdkRoot = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $SdkRoot) {
    $localProperties = Get-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Encoding UTF8
    $sdkLine = $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if (-not $sdkLine) { throw 'Pass -SdkRoot or configure sdk.dir in local.properties.' }
    $SdkRoot = $sdkLine.Substring('sdk.dir='.Length)
}
$sdkPath = (Resolve-Path -LiteralPath $SdkRoot).Path
$javaPath = Join-Path $JavaHome 'bin/java.exe'
$javacPath = Join-Path $JavaHome 'bin/javac.exe'
$androidJar = Join-Path $sdkPath 'platforms/android-35/android.jar'
$d8Jar = Join-Path $sdkPath 'build-tools/35.0.0/lib/d8.jar'
$source = Join-Path $projectRoot 'tools/remote-app-catalog/src/com/remotemix/catalog/RemoteAppCatalogProbe.java'
$buildDir = Join-Path $projectRoot 'app/build/remote-app-catalog'
$classesDir = Join-Path $buildDir 'classes'
$dexDir = Join-Path $buildDir 'dex'
New-Item -ItemType Directory -Path $classesDir, $dexDir -Force | Out-Null

& $javacPath -encoding UTF-8 -Xlint:-options -source 8 -target 8 -cp $androidJar -d $classesDir $source
if ($LASTEXITCODE -ne 0) { throw 'javac failed.' }
$classes = @(Get-ChildItem -LiteralPath $classesDir -Recurse -Filter '*.class' | ForEach-Object FullName)
& $javaPath -cp $d8Jar com.android.tools.r8.D8 --min-api 28 --lib $androidJar --output $dexDir @classes
if ($LASTEXITCODE -ne 0) { throw 'D8 failed.' }
$asset = Join-Path $projectRoot 'app/src/main/assets/remote-app-catalog.dex'
Copy-Item -LiteralPath (Join-Path $dexDir 'classes.dex') -Destination $asset -Force
Get-FileHash -LiteralPath $asset -Algorithm SHA256 | Select-Object Path, Hash
