import { Component, ElementRef, computed, effect, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconeComponent, NomIcone } from './icone.component';

/**
 * Confirmation d'un acte irréversible (signature, certification des fonds,
 * clôture, virement, archivage). Le récapitulatif montre ce qui va être
 * engagé ; une saisie de contrôle peut être exigée pour les actes les plus lourds.
 */
@Component({
  selector: 'app-confirmation',
  standalone: true,
  imports: [FormsModule, IconeComponent],
  template: `
    <div class="voile" (click)="surVoile($event)">
      <div class="dialogue" role="alertdialog" aria-modal="true" [attr.aria-labelledby]="'conf-titre'" [attr.aria-describedby]="'conf-desc'">
        <div class="dialogue__tete">
          <span class="dialogue__icone" [class.dialogue__icone--danger]="danger()"><app-icone [nom]="icone()" [taille]="24" /></span>
          <div>
            <h2 id="conf-titre">{{ titre() }}</h2>
            <p id="conf-desc">{{ message() }}</p>
          </div>
        </div>
        <div class="dialogue__corps">
          @if (recap().length) {
            <div class="dialogue__recap">
              @for (l of recap(); track l[0]) { <div><span>{{ l[0] }}</span><strong>{{ l[1] }}</strong></div> }
            </div>
          }
          @if (avertissement()) {
            <div class="bandeau bandeau--critique"><p style="margin:0"><strong>Action irréversible.</strong> {{ avertissement() }}</p></div>
          }
          <ng-content />
          @if (saisieAttendue()) {
            <div>
              <label for="conf-saisie">Pour confirmer, saisissez : <strong>{{ saisieAttendue() }}</strong></label>
              <input #saisieChamp id="conf-saisie" name="conf-saisie" autocomplete="off" [ngModel]="saisie()" (ngModelChange)="saisie.set($event)" />
            </div>
          }
        </div>
        <div class="dialogue__pied">
          <button class="bouton bouton--fantome" type="button" (click)="annuler.emit()">Annuler</button>
          <button #valider class="bouton" [class.bouton--danger]="danger()" type="button" [disabled]="!pret() || occupe()" (click)="confirmer.emit()">
            {{ occupe() ? 'Traitement…' : libelleAction() }}
          </button>
        </div>
      </div>
    </div>
  `,
  host: { '(document:keydown.escape)': 'annuler.emit()' },
})
export class ConfirmationComponent {
  readonly titre = input.required<string>();
  readonly message = input('');
  readonly libelleAction = input('Confirmer');
  readonly recap = input<[string, string][]>([]);
  readonly avertissement = input<string | null>(null);
  readonly saisieAttendue = input<string | null>(null);
  readonly icone = input<NomIcone>('signer');
  readonly danger = input(false);
  readonly occupe = input(false);
  readonly confirmer = output<void>();
  readonly annuler = output<void>();

  protected readonly saisie = signal('');
  protected readonly pret = computed(() => {
    const attendu = this.saisieAttendue();
    return !attendu || this.saisie().trim().toUpperCase() === attendu.toUpperCase();
  });

  private readonly champ = viewChild<ElementRef<HTMLInputElement>>('saisieChamp');
  private readonly valider = viewChild<ElementRef<HTMLButtonElement>>('valider');

  constructor() {
    // Le focus va à la saisie de contrôle, sinon au bouton d'action.
    effect(() => (this.champ() ?? this.valider())?.nativeElement.focus());
  }

  protected surVoile(e: MouseEvent): void {
    if (e.target === e.currentTarget && !this.occupe()) this.annuler.emit();
  }
}
