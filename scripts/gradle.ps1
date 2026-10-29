param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
$gameJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory -Filter 'jdk8*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($gameJdk) { $env:SOULCATCHER_GAME_JAVA = $gameJdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Java 17 is required. Set JAVA_HOME or run python scripts/bootstrap.py.' }
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
& (Join-Path $projectRoot 'gradlew.bat') @GradleArgs
exit $LASTEXITCODE
