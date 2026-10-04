param()
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$jdkCandidates=@($env:JAVA_HOME,'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot','C:\Users\HP\.jdks\ms-21.0.11')
$env:JAVA_HOME=$jdkCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin/jpackage.exe')) } | Select-Object -First 1
if(-not $env:JAVA_HOME){throw 'JDK 21 avec jpackage requis pour ce paquet.'}
$maven=Join-Path $PSScriptRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd'
& $maven "-Dmaven.repo.local=$PSScriptRoot\.tools\repository" package
if($LASTEXITCODE -ne 0){throw 'La compilation a échoué.'}
$maven=Join-Path $PSScriptRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd'
& $maven "-Dmaven.repo.local=$PSScriptRoot\.tools\repository" dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/app-input'
if($LASTEXITCODE -ne 0){throw 'Copie des dépendances impossible.'}
Copy-Item 'target/microgest-1.0.0.jar' 'target/app-input/' -Force
$destination=Join-Path $PSScriptRoot 'release'
if(Test-Path (Join-Path $destination 'MicroGest')){throw 'Le dossier release/MicroGest existe déjà. Déplacez-le avant de reconstruire le paquet.'}
& "$env:JAVA_HOME\bin\jpackage.exe" --type app-image --name MicroGest --input target/app-input --main-jar microgest-1.0.0.jar --main-class sn.microgest.Launcher --dest $destination --app-version 1.0.0 --vendor MicroGest --java-options '-Dfile.encoding=UTF-8' --java-options '-Dmicrogest.home=$APPDIR/..'
if($LASTEXITCODE -ne 0){throw 'Création du paquet Windows impossible.'}
New-Item -ItemType Directory -Force 'release/MicroGest/config' | Out-Null
Copy-Item 'config/application.example.properties' 'release/MicroGest/config/'
Copy-Item 'README.md' 'release/MicroGest/'
Write-Host 'Application créée : release/MicroGest/MicroGest.exe'


