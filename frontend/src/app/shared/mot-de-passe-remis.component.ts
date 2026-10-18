import { Component, input, output, signal } from '@angular/core';
import { IconeComponent } from './icone.component';

/** Affiche une seule fois le mot de passe provisoire d'un compte, à transmettre à son titulaire. */
@Component({
  selector: 'app-mot-de-passe-remis',
  standalone: true,
  imports: [IconeComponent],
  template: `
    <div class="voile">
      <div class="dialogue" role="alertdialog" aria-modal="true" aria-labelledby="mdp-titre">
        <div class="dialogue__tete">
          <span class="dialogue__icone"><app-icone nom="cle" [taille]="24" /></span>
          <div>
            <h2 id="mdp-titre">{{ titre() }}</h2>
            <p>Transmettez ce mot de passe provisoire à {{ nom() }} ({{ email() }}). Il devra le remplacer à sa première connexion.</p>
          </div>
        </div>
        <div class="dialogue__corps">
          <div class="mdp">
            <code>{{ motDePasse() }}</code>
            <button class="bouton bouton--secondaire bouton--petit" type="button" (click)="copier()">
              <app-icone [nom]="copie() ? 'coche' : 'copier'" [taille]="16" />{{ copie() ? 'Copié' : 'Copier' }}
            </button>
          </div>
          <div class="bandeau bandeau--vigilance"><p style="margin:0">Ce mot de passe ne sera plus affiché. Ne l'envoyez pas par un canal partagé (groupe, e-mail collectif).</p></div>
        </div>
        <div class="dialogue__pied">
          <button class="bouton" type="button" (click)="fermer.emit()">J'ai transmis le mot de passe</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .mdp { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px;
      border: 1px dashed var(--ligne-forte); border-radius: 8px; background: var(--surface-2); }
    code { font-size: 22px; font-weight: 700; letter-spacing: 0.06em; font-family: ui-monospace, 'SFMono-Regular', Menlo, monospace; }
  `],
})
export class MotDePasseRemisComponent {
  readonly titre = input('Mot de passe provisoire');
  readonly nom = input.required<string>();
  readonly email = input.required<string>();
  readonly motDePasse = input.required<string>();
  readonly fermer = output<void>();
  protected readonly copie = signal(false);

  protected copier(): void {
    navigator.clipboard?.writeText(this.motDePasse()).then(() => this.copie.set(true)).catch(() => this.copie.set(false));
  }
}
