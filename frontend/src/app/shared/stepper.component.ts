import { Component, computed, input } from '@angular/core';
import { StatutDossier } from '../core/models';
import { libelleStatut } from './badges';

const ORDRE: StatutDossier[] = [
  'DRAFT', 'PENDING_DOCS', 'DRAFTING', 'REVIEW', 'TAXING',
  'FUNDING', 'READY_TO_SIGN', 'SIGNED', 'POST_ACT', 'CLOSED',
];

/** Fil d'Ariane des 10 statuts : l'etape courante est nommee, jamais seulement coloree. */
@Component({
  selector: 'app-stepper',
  standalone: true,
  template: `
    <ol class="stepper">
      @for (s of etapes; track s; let i = $index) {
        <li class="stepper__etape" [class.franchie]="i < rang()" [class.courante]="i === rang()">
          <span class="stepper__pastille">{{ i < rang() || statut() === 'CLOSED' ? '✓' : i + 1 }}</span>
          <span class="stepper__libelle">{{ libelle(s) }}</span>
        </li>
      }
    </ol>
  `,
  styles: `
    .stepper { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); gap: 4px; list-style: none; margin: 0; padding: 0; }
    .stepper__etape { display: flex; flex-direction: column; align-items: center; gap: 8px; position: relative; text-align: center; }
    .stepper__etape:not(:last-child)::after {
      content: ''; position: absolute; top: 13px; left: calc(50% + 16px); right: calc(-50% + 16px);
      height: 2px; border-radius: 2px; background: var(--remplissage);
    }
    .stepper__etape.franchie:not(:last-child)::after { background: var(--teinte); }
    .stepper__pastille {
      width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center;
      font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums;
      background: var(--remplissage); color: var(--texte-3);
      transition: background-color 200ms ease, box-shadow 200ms ease;
    }
    .franchie .stepper__pastille { background: var(--teinte); color: var(--sur-teinte); }
    .courante .stepper__pastille {
      background: var(--surface); color: var(--teinte);
      box-shadow: inset 0 0 0 2px var(--teinte), 0 0 0 5px var(--teinte-fond);
    }
    .stepper__libelle { font-size: 12px; line-height: 1.25; color: var(--texte-3); }
    .franchie .stepper__libelle { color: var(--texte-2); }
    .courante .stepper__libelle { color: var(--texte); font-weight: 600; }
    @media (max-width: 900px) {
      .stepper { grid-template-columns: repeat(5, minmax(0, 1fr)); row-gap: 18px; }
      .stepper__etape:nth-child(5)::after { display: none; }
    }
  `,

})
export class StepperComponent {
  readonly statut = input.required<StatutDossier>();
  protected readonly etapes = ORDRE;
  protected readonly rang = computed(() => ORDRE.indexOf(this.statut()));
  protected libelle = libelleStatut;
}
