import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { Membre, Role } from '../core/models';
import { DateFrPipe } from '../shared/format.pipes';

interface Groupe {
  titre: string;
  description: string;
  roles: Role[];
}

const GROUPES: Groupe[] = [
  {
    titre: 'Officiers publics',
    description: "Notaires habilites a authentifier les actes ; leur signature et leur sceau engagent leur responsabilite.",
    roles: ['NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE', 'NOTAIRE_SALARIE'],
  },
  {
    titre: 'Redaction',
    description: 'Production des actes, sous la direction du principal clerc.',
    roles: ['PRINCIPAL_CLERC', 'CLERC'],
  },
  {
    titre: 'Fonctions support et post-acte',
    description: 'Fiscalite et sequestre, formalites, conservation des minutes, accueil.',
    roles: ['COMPTABLE_TAXATEUR', 'FORMALISTE', 'ARCHIVISTE', 'ASSISTANT'],
  },
];

@Component({
  selector: 'app-equipe',
  standalone: true,
  imports: [DateFrPipe],
  template: `
    <div class="page">
      <header class="page__entete">
        <h1>Organisation de l'etude</h1>
        <p>Hierarchie des officiers et collaborateurs ; chaque profil dispose de droits propres dans le cycle des dossiers.</p>
      </header>

      @for (g of groupes(); track g.titre) {
        <section class="carte">
          <div class="carte__entete"><div><h2>{{ g.titre }}</h2><p>{{ g.description }}</p></div></div>
          <div class="carte__corps grille grille--3">
            @for (m of g.membres; track m.utilisateur.id) {
              <article class="membre">
                <div class="membre__tete">
                  <span class="avatar" [class.avatar--or]="m.utilisateur.notaire">{{ m.utilisateur.initiales }}</span>
                  <div>
                    <p class="membre__nom">{{ m.utilisateur.nomComplet }}</p>
                    <span class="badge badge--petit" [class.badge--or]="m.utilisateur.notaire" [class.badge--neutre]="!m.utilisateur.notaire">
                      {{ m.utilisateur.roleLibelle }}</span>
                  </div>
                </div>
                <p class="tres-petit muet">{{ m.description }}</p>
                @if (m.referenceNomination) { <p class="tres-petit">{{ m.referenceNomination }}</p> }
                @if (m.notaireResponsable) { <p class="tres-petit">Exerce sous la responsabilite de <strong>{{ m.notaireResponsable }}</strong></p> }
                <dl class="membre__chiffres">
                  @if (m.utilisateur.notaire) { <div><dt>Dossiers supervises</dt><dd>{{ m.dossiersSupervises }}</dd></div> }
                  @if (m.utilisateur.role === 'CLERC' || m.utilisateur.role === 'PRINCIPAL_CLERC') { <div><dt>Dossiers rediges</dt><dd>{{ m.dossiersRediges }}</dd></div> }
                  <div><dt>Rendez-vous a venir</dt><dd>{{ m.rendezVousAVenir }}</dd></div>
                  <div><dt>Dans l'etude depuis</dt><dd>{{ m.dateEntree | dateFr }}</dd></div>
                </dl>
              </article>
            }
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .membre { border: 1px solid var(--ivoire-300); border-radius: 10px; padding: 14px; display: flex; flex-direction: column; gap: 6px; }
    .membre p { margin: 0; }
    .membre__tete { display: flex; gap: 10px; align-items: center; }
    .membre__nom { font-weight: 600; margin-bottom: 2px !important; }
    .avatar { width: 36px; height: 36px; font-size: 12px; }
    .avatar--or { background: var(--or-100); color: var(--or-600); }
    .membre__chiffres { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin: 6px 0 0; padding-top: 8px; border-top: 1px solid var(--ivoire-200); }
    .membre__chiffres dt { font-size: 10px; text-transform: uppercase; letter-spacing: 0.04em; color: var(--marine-400); }
    .membre__chiffres dd { margin: 0; font-weight: 600; font-variant-numeric: tabular-nums; }
  `,
})
export class EquipeComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly membres = signal<Membre[]>([]);

  protected readonly groupes = computed(() =>
    GROUPES.map((g) => ({
      ...g,
      membres: this.membres()
        .filter((m) => g.roles.includes(m.utilisateur.role))
        .sort((a, b) => g.roles.indexOf(a.utilisateur.role) - g.roles.indexOf(b.utilisateur.role)),
    })),
  );

  ngOnInit(): void {
    this.api.equipe().subscribe((m) => this.membres.set(m));
  }
}
