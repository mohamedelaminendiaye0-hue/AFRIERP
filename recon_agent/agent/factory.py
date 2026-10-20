# ===========================================================================
#  FABRIQUE D'AGENTS (le "socle" réutilisable)
#  -------------------------------------------------------------------------
#  C'est le cœur partagé par TOUS tes futurs agents.
#  Pour créer un nouvel agent, tu n'écris que deux choses :
#     1. ses outils (liste de @tool)
#     2. son system prompt (texte)
#  ... puis tu appelles make_agent(prompt, outils). Le reste est hérité.
# ===========================================================================
from langgraph.checkpoint.memory import MemorySaver
from langgraph.prebuilt import create_react_agent

from config.settings import settings


def _build_llm():
    """ÉTAPE A : construit le modèle selon le fournisseur choisi (.env).

    - "together" -> cloud, via l'API compatible OpenAI
    - "ollama"   -> local, via le serveur Ollama
    """
    provider = settings.provider.lower()

    if provider == "together":
        # 👉 ICI on utilise l'API Together AI (clé lue depuis .env).
        from langchain_openai import ChatOpenAI

        if not settings.together_api_key:
            raise RuntimeError(
                "TOGETHER_API_KEY manquante : renseignez-la dans le fichier .env "
                "(ou passez PROVIDER=ollama pour un modèle local)."
            )
        return ChatOpenAI(
            model=settings.together_model,
            api_key=settings.together_api_key,
            base_url=settings.together_base_url,
            temperature=0.1,   # bas = réponses stables/reproductibles
            timeout=60,
            max_retries=2,
        )

    if provider == "ollama":
        # 👉 ICI on utilise un modèle LOCAL via Ollama (aucune clé requise).
        # ⚠️ Le modèle doit supporter le tool-calling (qwen2.5, llama3.1...).
        from langchain_ollama import ChatOllama

        return ChatOllama(
            model=settings.ollama_model,
            base_url=settings.ollama_base_url,
            temperature=0.1,
        )

    raise ValueError(
        f"PROVIDER inconnu : {settings.provider!r}. Utilisez 'together' ou 'ollama'."
    )


def make_agent(system_prompt: str, tools: list):
    """ÉTAPE B : assemble un agent LangGraph complet, prêt à l'emploi.

    Fournit automatiquement :
      - la boucle raisonnement <-> outils (create_react_agent)
      - la mémoire / reprise d'état par thread_id (MemorySaver)

    Le garde-fou anti-boucle (recursion_limit) se règle à l'appel, dans main.py.
    """
    llm = _build_llm()
    return create_react_agent(
        model=llm,
        tools=tools,
        prompt=system_prompt,
        checkpointer=MemorySaver(),
    )
