# ===========================================================================
#  CONFIGURATION CENTRALE
#  Toutes les valeurs viennent du fichier .env (jamais de secret en dur).
#  Si une variable obligatoire manque, le programme s'arrête avec une
#  erreur claire (fail-fast) au lieu de partir avec une fausse valeur.
# ===========================================================================
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # --- Choix du fournisseur : "together" (cloud) ou "ollama" (local) -----
    provider: str = "together"

    # --- Together AI -------------------------------------------------------
    # together_api_key n'a PAS de valeur par défaut : si PROVIDER=together
    # et que la clé manque dans .env, l'erreur sera levée dans la factory.
    together_api_key: str = ""
    together_base_url: str = "https://api.together.xyz/v1"
    together_model: str = "Qwen/Qwen2.5-Coder-32B-Instruct"

    # --- Ollama (local) ----------------------------------------------------
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:7b"

    # --- Mission -----------------------------------------------------------
    target_url: str = "http://localhost:3000"
    allowed_hosts: list[str] = ["localhost", "127.0.0.1"]

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


# Instance unique importée partout dans le projet.
settings = Settings()
