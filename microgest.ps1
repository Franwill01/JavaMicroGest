param([ValidateSet('run','test','package')][string]$Action='run')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$javaCandidates=@($env:JAVA_HOME, 'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot', 'C:\Users\HP\.jdks\ms-21.0.11')
$selected=$javaCandidates | Where-Object {
  if (-not $_ -or -not (Test-Path (Join-Path $_ 'bin\javac.exe'))) { return $false }
  $releaseFile=Join-Path $_ 'release'
  if (-not (Test-Path $releaseFile)) { return $false }
  $versionLine=Get-Content -LiteralPath $releaseFile | Where-Object { $_ -like 'JAVA_VERSION=*' } | Select-Object -First 1
  $major=[regex]::Match($versionLine,'JAVA_VERSION="(\d+)').Groups[1].Value
  return $major -and [int]$major -ge 17
} | Select-Object -First 1
if(-not $selected){throw 'Installez un JDK 17 ou supérieur et définissez JAVA_HOME.'}
$env:JAVA_HOME=$selected
$env:PATH="$selected\bin;$env:PATH"
$maven=Join-Path $PSScriptRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd'
if(-not (Test-Path $maven)){
  New-Item -ItemType Directory -Force '.tools' | Out-Null
  Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile '.tools\maven.zip'
  Expand-Archive '.tools\maven.zip' '.tools' -Force
}
$repo="-Dmaven.repo.local=$PSScriptRoot\.tools\repository"
switch($Action){
 'run' { & $maven $repo 'javafx:run' }
 'test' { & $maven $repo 'test' }
 'package' { & $maven $repo 'package' }
}
exit $LASTEXITCODE
