param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $env:JAVA_HOME -and $localJdk) { $env:JAVA_HOME = $localJdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Java 25 is required. Set JAVA_HOME or run python scripts/bootstrap.py.' }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home' }
& (Join-Path $projectRoot 'gradlew.bat') @GradleArgs
exit $LASTEXITCODE
