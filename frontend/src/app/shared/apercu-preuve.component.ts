import { Component, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ApiService } from '../core/api.service';
import { Preuve } from '../core/models';
import { DateFrPipe } from './format.pipes';
import { IconeComponent } from './icone.component';

/** Aperçu d'une preuve (PDF ou image) dans la plateforme, avec téléchargement et empreinte. */
@Component({
  selector: 'app-apercu-preuve',
  standalone: true,
  imports: [DateFrPipe, IconeComponent],
  template: `
    <div class="voile" (click)="surVoile($event)">
      <div class="apercu" role="dialog" aria-modal="true" aria-labelledby="apercu-titre">
        <header class="apercu__tete">
          <div class="apercu__titre">
            <h2 id="apercu-titre">{{ preuve().typeLibelle }}</h2>
            <p>{{ preuve().nomFichier }} · {{ taille() }} · versée le {{ preuve().deposeLe | dateFr: true }} par {{ preuve().deposeParNom }}</p>
          </div>
          <div class="apercu__actions">
            <button class="bouton bouton--secondaire bouton--petit" type="button" [disabled]="!url()" (click)="telecharger()">
              <app-icone nom="telecharger" [taille]="16" />Télécharger</button>
            <button class="bouton bouton--fantome bouton--petit" type="button" aria-label="Fermer l'aperçu" (click)="fermer.emit()">
              <app-icone nom="fermer" [taille]="18" /></button>
          </div>
        </header>
        <div class="apercu__corps">
          @if (erreur()) {
            <p class="vide">Impossible d'afficher ce fichier. Téléchargez-le pour l'ouvrir.</p>
          } @else if (!url()) {
            <p class="vide">Chargement…</p>
          } @else if (preuve().typeContenu === 'application/pdf') {
            <iframe [src]="urlSure()" title="Aperçu du document"></iframe>
          } @else {
            <img [src]="urlSure()" [alt]="preuve().nomFichier" />
          }
        </div>
        <footer class="apercu__pied">
          <span>Empreinte SHA-256 : <code>{{ preuve().empreinteSha256 }}</code></span>
          @if (preuve().commentaire) { <span>{{ preuve().commentaire }}</span> }
        </footer>
      </div>
    </div>
  `,
  styles: [`
    .apercu { width: min(980px, 100%); height: min(92vh, 1100px); display: flex; flex-direction: column;
      background: var(--surface); border-radius: 12px; box-shadow: var(--ombre-flottante); overflow: hidden; animation: monte 220ms var(--ressort) both; }
    .apercu__tete { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 14px 18px; border-bottom: 1px solid var(--ligne); }
    .apercu__titre { min-width: 0; }
    .apercu__titre h2 { font-size: 18px; }
    .apercu__titre p { margin: 2px 0 0; color: var(--texte-2); font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .apercu__actions { display: flex; gap: 6px; flex-shrink: 0; }
    .apercu__corps { flex: 1; min-height: 0; background: #e9ebef; display: grid; place-items: center; }
    .apercu__corps iframe { width: 100%; height: 100%; border: 0; background: #fff; }
    .apercu__corps img { max-width: 100%; max-height: 100%; object-fit: contain; }
    .apercu__pied { display: flex; flex-wrap: wrap; gap: 6px 18px; padding: 10px 18px; border-top: 1px solid var(--ligne); font-size: 13px; color: var(--texte-2); }
    .apercu__pied code { font-size: 12px; word-break: break-all; color: var(--texte); }
  `],
  host: { '(document:keydown.escape)': 'fermer.emit()' },
})
export class ApercuPreuveComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly assainisseur = inject(DomSanitizer);

  readonly preuve = input.required<Preuve>();
  readonly fermer = output<void>();

  protected readonly url = signal<string | null>(null);
  protected readonly urlSure = signal<SafeResourceUrl | null>(null);
  protected readonly erreur = signal(false);

  ngOnInit(): void {
    this.api.contenuPreuve(this.preuve().id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(new Blob([blob], { type: this.preuve().typeContenu }));
        this.url.set(url);
        // URL locale (blob:) créée à partir d'un fichier contrôlé par le serveur (PDF, PNG ou JPEG).
        this.urlSure.set(this.assainisseur.bypassSecurityTrustResourceUrl(url));
      },
      error: () => this.erreur.set(true),
    });
  }

  ngOnDestroy(): void {
    const url = this.url();
    if (url) URL.revokeObjectURL(url);
  }

  protected taille(): string {
    const o = this.preuve().taille;
    return o >= 1024 * 1024 ? `${(o / 1024 / 1024).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo`
      : `${Math.max(1, Math.round(o / 1024))} Ko`;
  }

  protected telecharger(): void {
    const url = this.url();
    if (!url) return;
    const a = document.createElement('a');
    a.href = url;
    a.download = this.preuve().nomFichier;
    a.click();
  }

  protected surVoile(e: MouseEvent): void {
    if (e.target === e.currentTarget) this.fermer.emit();
  }
}
