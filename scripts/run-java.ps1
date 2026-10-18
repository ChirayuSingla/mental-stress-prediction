param([switch]$Demo, [switch]$Packaged, [string]$SocketDirectory)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
$oldMode = $env:APP_MODE
# Avoid the MSIX-virtualized LocalAppData temp tree as well as long paths.
$socketDir = $SocketDirectory
if (-not $socketDir) {
    $socketRoot = $projectRoot
    while ([Text.Encoding]::UTF8.GetByteCount((Join-Path $socketRoot '.sockets')) -gt 75) {
        $parent = Split-Path -Parent $socketRoot
        if (-not $parent -or $parent -eq $socketRoot) { throw 'Pass -SocketDirectory with a short writable workspace path.' }
        $socketRoot = $parent
    }
    $socketDir = Join-Path $socketRoot '.sockets'
}
New-Item -ItemType Directory -Force -Path $socketDir | Out-Null
$env:JAVA_TOOL_OPTIONS = ($oldJavaOptions + ' "-Djdk.net.unixdomain.tmpdir=' + $socketDir + '"').Trim()
if ($Demo) { $env:APP_MODE = 'demo' }
Push-Location (Join-Path $projectRoot 'java-app')
try {
    if ($Packaged) {
        if (-not (Test-Path -LiteralPath 'target/stress-prediction-app-1.0.0.jar')) { throw 'Run mvn package first.' }
        & java -jar target/stress-prediction-app-1.0.0.jar
    } else { & mvn javafx:run }
    if ($LASTEXITCODE -ne 0) { throw "Java application exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    $env:APP_MODE = $oldMode
}
