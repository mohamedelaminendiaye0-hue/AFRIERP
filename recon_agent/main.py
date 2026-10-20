# ===========================================================================
#  POINT D'ENTRÉE — lance une mission de reconnaissance
#  -------------------------------------------------------------------------
#  Exécution :
#     python main.py                          (cible par défaut du .env)
#     python main.py --target http://localhost:3000
# ===========================================================================
import argparse
import uuid

from agents.recon import build_recon_agent
from config.settings import settings


def run(target: str) -> str:
    # ÉTAPE 1 : construire l'agent (choisit Together ou Ollama selon .env).
    agent = build_recon_agent()

    # ÉTAPE 2 : la consigne de mission envoyée à l'agent.
    query = (
        f"Effectue une reconnaissance complète sur la cible : {target}. "
        "Analyse les en-têtes de sécurité et identifie les formulaires et "
        "points d'entrée. Termine par une synthèse des faiblesses potentielles."
    )

    # ÉTAPE 3 : configuration d'exécution.
    #   - thread_id      : identité de la mission (mémoire / reprise d'état)
    #   - recursion_limit: garde-fou anti-boucle-infinie
    config = {
        "configurable": {"thread_id": f"recon-{uuid.uuid4().hex[:8]}"},
        "recursion_limit": 25,
    }

    # ÉTAPE 4 : exécution en streaming (on voit chaque étape de raisonnement).
    print(f"\n🔍 Mission lancée sur {target}\n")
    final = None
    for step in agent.stream(
        {"messages": [("user", query)]}, config, stream_mode="values"
    ):
        final = step["messages"][-1]
        final.pretty_print()

    return final.content if final else "(aucune sortie)"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Agent de reconnaissance (LangGraph)")
    parser.add_argument(
        "--target", default=settings.target_url,
        help="URL cible (doit appartenir à ALLOWED_HOSTS)",
    )
    args = parser.parse_args()

    print(f"🚀 Initialisation de l'agent (provider = {settings.provider})...")
    report = run(args.target)

    print("\n📄 RAPPORT DE RECONNAISSANCE FINAL :\n")
    print(report)
