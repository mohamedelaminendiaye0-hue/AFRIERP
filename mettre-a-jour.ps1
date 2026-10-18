# Mise à jour de Notarius (Windows / PowerShell)
#   .\mettre-a-jour.ps1                       -> dernière version + base de démonstration réinitialisée
#   .\mettre-a-jour.ps1 -ConserverDonnees     -> garde la base PostgreSQL existante
#   .\mettre-a-jour.ps1 -Branche main         -> autre branche
# Si PowerShell refuse le script : Set-ExecutionPolicy -Scope Process Bypass
param(
    [string]$Branche = "claude/magical-cerf-eqyt43",
    [switch]$ConserverDonnees
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "1/5  Récupération du code ($Branche)..." -ForegroundColor Cyan
git fetch origin
git checkout $Branche
git pull origin $Branche

Write-Host "2/5  Arrêt des anciens conteneurs qui occupent les ports 4200, 8080 et 5432..." -ForegroundColor Cyan
foreach ($port in 4200, 8080, 5432) {
    docker ps -q --filter "publish=$port" | ForEach-Object { docker rm -f $_ | Out-Null }
}

Write-Host "3/5  Arrêt de la plateforme actuelle..." -ForegroundColor Cyan
if ($ConserverDonnees) { docker compose down } else { docker compose down -v }

Write-Host "4/5  Reconstruction des images (sans cache)..." -ForegroundColor Cyan
docker compose build --no-cache --pull

Write-Host "5/5  Démarrage..." -ForegroundColor Cyan
docker compose up -d

Write-Host "Attente de l'application..." -ForegroundColor Cyan
for ($i = 0; $i -lt 90; $i++) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:4200/api/identite" -TimeoutSec 3
        if ($r.StatusCode -eq 200) { break }
    } catch { Start-Sleep -Seconds 2 }
}
Write-Host ""
Write-Host "Notarius est prêt : http://localhost:4200" -ForegroundColor Green
Write-Host "La page de connexion doit afficher « version 0.3.0 » en bas. Sinon, rechargez avec Ctrl+F5."
Write-Host "Comptes de démonstration : mot de passe notarius2026 · super administrateur : admin@notarius.sn"
