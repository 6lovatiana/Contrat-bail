param(
    [switch]$NoLaunch,
    [string[]]$ApplicationArguments
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "=== Nettoyage et compilation du projet ===" -ForegroundColor Cyan

$maven = if (Test-Path (Join-Path $projectRoot "mvnw.cmd")) {
    Join-Path $projectRoot "mvnw.cmd"
} else {
    "mvn"
}

& $maven clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "La compilation Maven a échoué (code $LASTEXITCODE)."
}

Write-Host "`nCompilation terminée avec succès." -ForegroundColor Green
$jarPath = Join-Path $projectRoot "target\contrat-bail-0.0.1-SNAPSHOT.jar"
Write-Host "Artefact : $jarPath" -ForegroundColor Green

if (-not $NoLaunch) {
    Write-Host "`n=== Lancement de l'application ===" -ForegroundColor Cyan
    Write-Host "Application disponible sur http://localhost:1404/" -ForegroundColor Green

    & java -jar $jarPath @ApplicationArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Le lancement de l'application a échoué (code $LASTEXITCODE)."
    }
}