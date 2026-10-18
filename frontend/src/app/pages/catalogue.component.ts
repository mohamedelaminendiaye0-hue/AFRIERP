import { Component, inject } from '@angular/core';
import { CatalogueService } from '../core/catalogue.service';

/** Reference des actes et demarches traites par l'etude, avec leurs parametres. */
@Component({
  selector: 'app-catalogue',
  standalone: true,
  template: `
    <div class="page">
      <header class="page__entete">
        <h1>Catalogue des actes et démarches</h1>
        <p>Pour chaque acte : assiette, fiscalité, émolument, séquestre et formalités post-acte. Paramètres de démonstration (barème SN-2026.1), à valider juridiquement.</p>
      </header>
      @for (g of catalogue.groupes(); track g.domaine) {
        <section class="carte">
          <div class="carte__entete"><div><h2>{{ g.libelle }}</h2><p>{{ g.types.length }} types d'actes</p></div></div>
          <div class="table-defilante">
            <table>
              <thead>
                <tr><th>Acte</th><th>Assiette</th><th>Enregistrement</th><th>Émolument</th><th>Séquestre</th><th>Formalités post-acte</th></tr>
              </thead>
              <tbody>
                @for (t of g.types; track t.code) {
                  <tr>
                    <td class="petit"><strong>{{ t.libelle }}</strong> <span class="tres-petit muet chiffres">({{ t.codeReference }})</span>
                      @if (t.complexe) { <br /><span class="badge badge--petit badge--or">Visa principal clerc</span> }
                      @if (t.delaiOppositionJours) { <br /><span class="badge badge--petit badge--vigilance">Opposition {{ t.delaiOppositionJours }} j</span> }</td>
                    <td class="petit">{{ t.assietteLibelle ?? '— (acte à droit fixe)' }}</td>
                    <td class="petit">{{ t.enregistrement }} @if (t.publiciteFonciere) { <br /><span class="tres-petit muet">+ publicité foncière</span> }</td>
                    <td class="petit">{{ t.emolument }}</td>
                    <td class="petit">{{ t.sequestre === 'AUCUN' ? 'Frais' : t.sequestre === 'PRIX' ? 'Frais + prix' : 'Frais + capital' }}</td>
                    <td class="petit">{{ t.formalites.length ? t.formalites.join(', ') : '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
})
export class CatalogueComponent {
  protected readonly catalogue = inject(CatalogueService);
}
