import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, lireErreur } from '../core/api.service';
import { IdentiteService, POLICES, texteSur } from '../core/identite.service';
import { IdentiteEtude } from '../core/models';
import { DateFrPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';

const LOGO_MAX_OCTETS = 300 * 1024;

/**
 * Marque blanche (super administrateur) : nom, logo, couleurs et police de
 * l'étude. L'aperçu se met à jour pendant la saisie ; l'enregistrement
 * applique l'identité à toute la plateforme et à la page de connexion.
 */
@Component({
  selector: 'app-identite',
  standalone: true,
  imports: [FormsModule, DateFrPipe, IconeComponent],
  templateUrl: './identite.component.html',
  styleUrl: './identite.component.scss',
})
export class IdentiteComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly service = inject(IdentiteService);

  protected readonly polices = POLICES;
  protected readonly principales = ['#1E3A5F', '#123B2F', '#5B1F2B', '#2E2A24', '#1F4E79', '#3B2F63'];
  protected readonly accents = ['#C9A227', '#D4A373', '#9C6644', '#7A9E7E', '#B08D57'];

  protected readonly brouillon = signal<IdentiteEtude>({ ...this.service.identite() });
  protected readonly envoi = signal(false);
  protected readonly message = signal<{ ok: boolean; texte: string } | null>(null);

  protected readonly pileApercu = computed(() => POLICES.find((p) => p.valeur === this.brouillon().police)?.pile ?? 'inherit');
  protected readonly surPrincipale = computed(() => texteSur(this.brouillon().couleurPrincipale));
  protected readonly surAccent = computed(() => texteSur(this.brouillon().couleurAccent));

  ngOnInit(): void {
    this.api.identite().subscribe((i) => this.brouillon.set({ ...i }));
  }

  protected maj<K extends keyof IdentiteEtude>(cle: K, valeur: IdentiteEtude[K]): void {
    this.brouillon.update((b) => ({ ...b, [cle]: valeur }));
    this.message.set(null);
  }

  protected importerLogo(evenement: Event): void {
    const fichier = (evenement.target as HTMLInputElement).files?.[0];
    if (!fichier) return;
    if (!['image/png', 'image/jpeg', 'image/svg+xml'].includes(fichier.type)) {
      this.message.set({ ok: false, texte: 'Le logo doit être une image PNG, JPEG ou SVG.' });
      return;
    }
    if (fichier.size > LOGO_MAX_OCTETS) {
      this.message.set({ ok: false, texte: 'Le logo dépasse 300 Ko : réduisez-le avant de l’importer.' });
      return;
    }
    const lecteur = new FileReader();
    lecteur.onload = () => this.maj('logo', lecteur.result as string);
    lecteur.readAsDataURL(fichier);
  }

  protected enregistrer(): void {
    this.envoi.set(true);
    this.api.enregistrerIdentite(this.brouillon()).subscribe({
      next: (i) => {
        this.envoi.set(false);
        this.brouillon.set({ ...i });
        this.service.appliquer(i);
        this.message.set({ ok: true, texte: "L'identité de l'étude est enregistrée et appliquée à toute la plateforme." });
      },
      error: (e) => {
        this.envoi.set(false);
        const err = lireErreur(e);
        this.message.set({ ok: false, texte: err.blocages[0]?.message ?? err.message });
      },
    });
  }

  protected retablir(): void {
    this.brouillon.set({ ...this.service.identite() });
    this.message.set(null);
  }
}
