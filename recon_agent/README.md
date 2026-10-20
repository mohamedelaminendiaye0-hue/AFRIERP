# Agent de reconnaissance (LangGraph)

Pipeline agent prête à l'emploi, compatible **Together AI** (cloud) et **Ollama** (local).

## Structure

```
recon_agent/
├── config/settings.py     # Configuration (.env) — centralisée, validée
├── agent/factory.py       # SOCLE réutilisable : make_agent() + choix du LLM
├── tools/recon_tools.py   # Outils de l'agent (scope-guard + anti-injection)
├── agents/recon.py        # Agent concret = prompt + outils (modèle à copier)
├── main.py                # Point d'entrée
├── requirements.txt
└── .env.example           # À copier en .env
```

## Démarrage (4 étapes)

```bash
# 1. Dépendances
pip install -r requirements.txt

# 2. Configuration
cp .env.example .env
#    puis éditez .env :
#      - PROVIDER=together  -> renseignez TOGETHER_API_KEY
#      - PROVIDER=ollama    -> lancez "ollama serve" + un modèle "tools"

# 3. (cible) : Juice Shop local, par exemple
#    docker run -d -p 3000:3000 bkimminich/juice-shop

# 4. Lancer
python main.py --target http://localhost:3000
```

## Basculer Together <-> Ollama

Un seul changement dans `.env` :
```dotenv
PROVIDER=ollama          # ou "together"
```
⚠️ Avec Ollama, choisissez un modèle qui supporte le **tool-calling**
(ex. `qwen2.5`, `llama3.1`, `mistral-nemo`), sinon l'agent n'appelle pas ses outils.

## Créer un nouvel agent

1. Écrire ses outils dans `tools/`
2. Copier `agents/recon.py`, changer le prompt et la liste d'outils
3. Appeler `make_agent(prompt, outils)` — tout le socle est hérité.
