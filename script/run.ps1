# Arrêter l'exécution en cas d'erreur
\$ErrorActionPreference = "Stop"

Write-Host "=== 1. Nettoyage et Compilation de l'application Java ===" -ForegroundColor Cyan
# Utilise le wrapper Maven si disponible, sinon 'mvn'
if (Test-Path "..\mvnw") {
    Start-Process -FilePath "..\mvnw" -ArgumentList "clean package -DskipTests" -Wait -NoNewWindow
} else {
    Start-Process -FilePath "mvn" -ArgumentList "clean package -DskipTests" -Wait -NoNewWindow
}

Write-Host "`n=== 2. Construction et lancement avec Docker Compose ===" -ForegroundColor Cyan
cd ..
docker-compose down
docker-compose up --build -d

Write-Host "`n=== Application démarrée avec succès ! ===" -ForegroundColor Green
