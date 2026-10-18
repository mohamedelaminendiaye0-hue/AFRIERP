import { Component, computed, input, output, signal, Signal } from '@angular/core';

/**
 * Pagination côté client : découpe une liste filtrée en pages. La page revient
 * à 1 dès que la liste change de longueur (nouveau filtre, nouvelle recherche).
 */
export class Pagination<T> {
  readonly page = signal(1);
  readonly taille = signal(10);

  constructor(private readonly source: Signal<T[]>, tailleInitiale = 10) {
    this.taille.set(tailleInitiale);
  }

  readonly total = computed(() => this.source().length);
  readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / this.taille())));
  /** Page courante bornée : un filtre qui réduit la liste ne laisse jamais une page vide. */
  readonly courante = computed(() => Math.min(this.page(), this.pages()));
  readonly elements = computed(() => {
    const debut = (this.courante() - 1) * this.taille();
    return this.source().slice(debut, debut + this.taille());
  });

  aller(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.pages()));
  }

  changerTaille(taille: number): void {
    this.taille.set(taille);
    this.page.set(1);
  }

  reinitialiser(): void {
    this.page.set(1);
  }
}

/** Barre de pagination : « 1–25 sur 132 », pages numérotées, taille de page. */
@Component({
  selector: 'app-pagination',
  standalone: true,
  template: `
    @if (total() > 0) {
      <nav class="pagination" aria-label="Pagination">
        <span class="pagination__resume">{{ debut() }}–{{ fin() }} sur {{ total() }} {{ libelle() }}</span>
        @if (pages() > 1) {
          <div class="pagination__pages">
            <button type="button" class="pagination__bouton" [disabled]="page() === 1" (click)="aller(page() - 1)" aria-label="Page précédente">‹</button>
            @for (p of numeros(); track $index) {
              @if (p === 0) { <span class="pagination__ellipse" aria-hidden="true">…</span> }
              @else {
                <button type="button" class="pagination__bouton" [class.actif]="p === page()" [attr.aria-current]="p === page() ? 'page' : null"
                        (click)="aller(p)">{{ p }}</button>
              }
            }
            <button type="button" class="pagination__bouton" [disabled]="page() === pages()" (click)="aller(page() + 1)" aria-label="Page suivante">›</button>
          </div>
        }
        <label class="pagination__taille">
          <span>Par page</span>
          <select [value]="taille()" (change)="tailleChange.emit(+$any($event.target).value)">
            @for (t of tailles; track t) { <option [value]="t" [selected]="t === taille()">{{ t }}</option> }
          </select>
        </label>
      </nav>
    }
  `,
  styles: [`
    .pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
      padding: 12px 20px; border-top: 1px solid var(--ligne); font-size: 14.5px; }
    .pagination__resume { color: var(--texte-2); font-variant-numeric: tabular-nums; }
    .pagination__pages { display: flex; gap: 4px; align-items: center; }
    .pagination__bouton { min-width: 36px; height: 36px; padding: 0 8px; border-radius: 8px; border: 1px solid transparent;
      background: transparent; font: inherit; font-weight: 700; color: var(--texte); cursor: pointer; font-variant-numeric: tabular-nums; }
    .pagination__bouton:hover:not(:disabled):not(.actif) { background: var(--remplissage); }
    .pagination__bouton.actif { background: var(--primaire); color: var(--sur-primaire); }
    .pagination__bouton:disabled { color: #9aa3b0; cursor: not-allowed; }
    .pagination__ellipse { padding: 0 4px; color: var(--texte-2); }
    .pagination__taille { display: flex; align-items: center; gap: 8px; margin: 0; font-weight: 400; color: var(--texte-2); font-size: 14.5px; }
    .pagination__taille select { width: auto; min-height: 36px; padding: 0 30px 0 10px; font-size: 14.5px; }
  `],
})
export class PaginationComponent {
  readonly total = input.required<number>();
  readonly page = input.required<number>();
  readonly taille = input.required<number>();
  readonly libelle = input('');
  readonly pageChange = output<number>();
  readonly tailleChange = output<number>();
  protected readonly tailles = [10, 25, 50, 100];

  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / this.taille())));
  protected readonly debut = computed(() => (this.total() === 0 ? 0 : (this.page() - 1) * this.taille() + 1));
  protected readonly fin = computed(() => Math.min(this.total(), this.page() * this.taille()));
  /** Numéros affichés : première, dernière et voisines de la page courante ; 0 = ellipse. */
  protected readonly numeros = computed(() => {
    const n = this.pages();
    const p = this.page();
    const garder = new Set([1, n, p - 1, p, p + 1].filter((x) => x >= 1 && x <= n));
    const tries = [...garder].sort((a, b) => a - b);
    const sortie: number[] = [];
    tries.forEach((x, i) => {
      if (i > 0 && x - tries[i - 1] > 1) sortie.push(0);
      sortie.push(x);
    });
    return sortie;
  });

  protected aller(p: number): void {
    this.pageChange.emit(p);
  }
}
