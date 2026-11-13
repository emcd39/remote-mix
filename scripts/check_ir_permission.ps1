param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath
)

$sdkLine = Get-Content -LiteralPath 'local.properties' | Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
if (-not $sdkLine) { throw 'Android SDK path is missing from local.properties' }
$sdkRoot = $sdkLine.Substring('sdk.dir='.Length).Replace('/', [IO.Path]::DirectorySeparatorChar)
$aapt = Join-Path $sdkRoot 'build-tools/35.0.0/aapt.exe'
if (-not (Test-Path -LiteralPath $aapt)) { throw "aapt.exe not found: $aapt" }

$permissions = & $aapt dump permissions $ApkPath
if ($LASTEXITCODE -ne 0) { throw "Could not inspect APK permissions: $ApkPath" }
if (-not ($permissions -match "uses-permission: name='android\.permission\.TRANSMIT_IR'")) {
    throw 'IR regression: APK does not declare android.permission.TRANSMIT_IR'
}
Write-Output 'IR permission present in packaged APK'
