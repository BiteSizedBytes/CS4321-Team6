param([string]$DataFile = 'data/reservations.properties')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } elseif (Test-Path "$env:USERPROFILE/.jdks/openjdk-27/bin/javac.exe") { "$env:USERPROFILE/.jdks/openjdk-27/bin" } else { '' }
$compiler = if ($jdkBin) { Join-Path $jdkBin 'javac.exe' } else { 'javac' }
$runtime = if ($jdkBin) { Join-Path $jdkBin 'java.exe' } else { 'java' }
New-Item -ItemType Directory -Force out/app | Out-Null
$sources = @(Get-ChildItem src -Recurse -Filter '*.java' | ForEach-Object FullName)
& $compiler --release 17 -encoding UTF-8 -d out/app @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
& $runtime -cp out/app Main $DataFile
if ($LASTEXITCODE -ne 0) { throw 'Application failed.' }
