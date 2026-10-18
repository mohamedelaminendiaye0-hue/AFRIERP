#!/usr/bin/env sh
# Mise à jour de Notarius (Linux / macOS)
#   ./mettre-a-jour.sh                  -> dernière version + base de démonstration réinitialisée
#   CONSERVER_DONNEES=1 ./mettre-a-jour.sh
#   BRANCHE=main ./mettre-a-jour.sh
set -e
cd "$(dirname "$0")"
BRANCHE="${BRANCHE:-claude/magical-cerf-eqyt43}"

echo "1/5  Récupération du code ($BRANCHE)..."
git fetch origin && git checkout "$BRANCHE" && git pull origin "$BRANCHE"

echo "2/5  Arrêt des anciens conteneurs qui occupent les ports 4200, 8080 et 5432..."
for port in 4200 8080 5432; do
  ids=$(docker ps -q --filter "publish=$port"); [ -n "$ids" ] && docker rm -f $ids >/dev/null || true
done

echo "3/5  Arrêt de la plateforme actuelle..."
if [ -n "$CONSERVER_DONNEES" ]; then docker compose down; else docker compose down -v; fi

echo "4/5  Reconstruction des images (sans cache)..."
docker compose build --no-cache --pull

echo "5/5  Démarrage..."
docker compose up -d

i=0; until curl -fs http://localhost:4200/api/identite >/dev/null 2>&1 || [ $i -ge 90 ]; do i=$((i+1)); sleep 2; done
echo
echo "Notarius est prêt : http://localhost:4200 (la page de connexion affiche « version 0.3.0 »)."
echo "Comptes de démonstration : mot de passe notarius2026 · super administrateur : admin@notarius.sn"
