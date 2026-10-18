import { Injectable, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { IdentiteEtude } from './models';

const PAR_DEFAUT: IdentiteEtude = {
  nom: 'Étude notariale',
  sousTitre: null,
  monogramme: 'N',
  couleurPrincipale: '#1E3A5F',
  couleurAccent: '#C9A227',
  police: 'Source Sans 3',
  logo: null,
  adresse: null,
  telephone: null,
  email: null,
  ninea: null,
  mentionPiedDePage: null,
};

/** Polices proposées en marque blanche (auto-hébergées ou natives). */
export const POLICES: { valeur: string; libelle: string; pile: string }[] = [
  { valeur: 'Source Sans 3', libelle: 'Source Sans', pile: "'Source Sans 3'" },
  { valeur: 'IBM Plex Sans', libelle: 'IBM Plex Sans', pile: "'IBM Plex Sans'" },
  { valeur: 'Systeme', libelle: 'Police du système', pile: 'system-ui' },
  { valeur: 'Georgia', libelle: 'Georgia (serif)', pile: 'Georgia, serif' },
];

/**
 * Marque blanche : charge l'identité de l'étude et l'applique à toute
 * l'interface (variables CSS, police, titre de l'onglet).
 */
@Injectable({ providedIn: 'root' })
export class IdentiteService {
  private readonly api = inject(ApiService);
  private readonly titre = inject(Title);
  readonly identite = signal<IdentiteEtude>(PAR_DEFAUT);

  async charger(): Promise<void> {
    try {
      this.appliquer(await firstValueFrom(this.api.identite()));
    } catch {
      this.appliquer(PAR_DEFAUT);
    }
  }

  appliquer(i: IdentiteEtude): void {
    this.identite.set(i);
    const racine = document.documentElement.style;
    racine.setProperty('--primaire', i.couleurPrincipale);
    racine.setProperty('--accent', i.couleurAccent);
    racine.setProperty('--sur-primaire', texteSur(i.couleurPrincipale));
    racine.setProperty('--sur-accent', texteSur(i.couleurAccent));
    racine.setProperty('--police', POLICES.find((p) => p.valeur === i.police)?.pile ?? "'Source Sans 3'");
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', i.couleurPrincipale);
    this.titre.setTitle(i.nom);
  }

  /** Titre d'onglet : « Page — Nom de l'étude ». */
  titrePage(page: string): string {
    return `${page} — ${this.identite().nom}`;
  }
}

/** Texte lisible sur une couleur de fond : encre foncée sur fond clair, blanc sinon. */
export function texteSur(hex: string): string {
  const n = parseInt(hex.replace('#', ''), 16);
  const canal = (c: number) => {
    const v = c / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  const l = 0.2126 * canal((n >> 16) & 255) + 0.7152 * canal((n >> 8) & 255) + 0.0722 * canal(n & 255);
  // Contraste du blanc (1,05 / (l + 0,05)) contre celui de l'encre (l + 0,05) / 0,0606.
  return 1.05 / (l + 0.05) >= (l + 0.05) / 0.0606 ? '#ffffff' : '#0f1b2d';
}
