import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Icônes au trait, 24 px, dessinées pour être lues à 18–20 px. */
export const ICONES = {
  accueil: 'M3 11.5 12 4l9 7.5M5.5 10v10h13V10',
  dossiers: 'M3 7.5A2.5 2.5 0 0 1 5.5 5H9l2 2h7.5A2.5 2.5 0 0 1 21 9.5v7a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 16.5z',
  clients: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20.5c0-4 3.4-6.5 7.5-6.5s7.5 2.5 7.5 6.5',
  agenda: 'M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM4 10h16M8 3v4M16 3v4',
  repertoire: 'M6 3h9l4 4v14H6zM14.5 3v4.5H19M9.5 11h6M9.5 14.5h6M9.5 18h4',
  minutier: 'M3.5 4h17v4h-17zM5 8v10.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V8M10 12h4',
  fonds: 'M3 10 12 4l9 6M5.5 10v8M10 10v8M14 10v8M18.5 10v8M3 20h18',
  equipe: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20a6.5 6.5 0 0 1 13 0M16 4.3a3.5 3.5 0 0 1 0 6.4M18 14.3a6.5 6.5 0 0 1 3.5 5.7',
  catalogue: 'M5 4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5zM5 19.5A1.5 1.5 0 0 0 6.5 21H19v-3M9 7h6',
  calcul: 'M6.5 3h11A1.5 1.5 0 0 1 19 4.5v15a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19.5v-15A1.5 1.5 0 0 1 6.5 3zM8.5 7h7M8.5 12h1.5M14 12h1.5M8.5 16h1.5M14 16h1.5',
  identite: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3.6 9h16.8M3.6 15h16.8M12 3c2.3 2.4 3.5 5.4 3.5 9s-1.2 6.6-3.5 9c-2.3-2.4-3.5-5.4-3.5-9S9.7 5.4 12 3z',
  plus: 'M12 5v14M5 12h14',
  archiver: 'M3.5 4h17v4h-17zM5 8v10.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V8M10 12h4',
  cloche: 'M6 9.5a6 6 0 1 1 12 0c0 4.5 1.8 6 1.8 6H4.2S6 14 6 9.5zM10 19a2 2 0 0 0 4 0',
  recherche: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4',
  sortie: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4',
  menu: 'M4 7h16M4 12h16M4 17h16',
  fermer: 'M6 6l12 12M18 6 6 18',
  signer: 'M3 20c3-1 5-4 8-4s3 2 5 2 3-1 5-3M14 4l6 6-9 9H5v-6z',
  alerte: 'M12 4 2.5 20h19zM12 10v4.5M12 17.5v.5',
  telecharger: 'M12 3v12M7 10l5 5 5-5M4 21h16',
  imprimer: 'M7 8V3h10v5M7 17H5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2M7 14h10v7H7z',
  crayon: 'M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4',
  coche: 'M5 12.5 10 17l9-10',
  fonds2: 'M4 7h16v10H4zM12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  trombone: 'M20 11.5l-8.3 8.3a5 5 0 0 1-7.1-7.1l8.6-8.6a3.4 3.4 0 0 1 4.8 4.8l-8.6 8.6a1.7 1.7 0 0 1-2.4-2.4l8-8',
  cadenas: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3M12 15v2',
  journal: 'M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM9 8h6M9 12h6M9 16h3',
  cle: 'M14.5 9.5a4 4 0 1 1-3.2 3.9L4 20.7V17h2.5v-2.5H9l2.3-2.3',
  bouclier: 'M12 3 5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6z',
  copier: 'M9 9h10v11H9zM5 15V4h10',
  oeil: 'M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  serveur: 'M4 4h16v6H4zM4 14h16v6H4zM8 7h.01M8 17h.01',
} as const;

export type NomIcone = keyof typeof ICONES;

@Component({
  selector: 'app-icone',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg viewBox="0 0 24 24" aria-hidden="true" [attr.width]="taille()" [attr.height]="taille()"><path [attr.d]="chemin()" /></svg>`,
  styles: [`:host{display:inline-flex;flex-shrink:0} svg{fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}`],
})
export class IconeComponent {
  readonly nom = input.required<NomIcone>();
  readonly taille = input(20);
  protected chemin() {
    return ICONES[this.nom()];
  }
}
