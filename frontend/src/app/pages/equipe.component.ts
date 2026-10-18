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
    description: "Notaires habilités à authentifier les actes ; leur signature et leur sceau engagent leur responsabilité.",
    roles: ['NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE', 'NOTAIRE_SALARIE'],
  },
  {
    titre: 'Rédaction',
    description: 'Production des actes, sous la direction du principal clerc.',
    roles: ['PRINCIPAL_CLERC', 'CLERC'],
  },
  {
    titre: 'Fonctions support et post-acte',
    description: 'Fiscalité et séquestre, formalités, conservation des minutes, accueil.',
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
        <h1>Organisation de l'étude</h1>
        <p>Hiérarchie des officiers et collaborateurs ; chaque profil dispose de droits propres dans le cycle des dossiers.</p>
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
                @if (m.notaireResponsable) { <p class="tres-petit">Exerce sous la responsabilité de <strong>{{ m.notaireResponsable }}</strong></p> }
                <dl class="membre__chiffres">
                  @if (m.utilisateur.notaire) { <div><dt>Dossiers supervisés</dt><dd>{{ m.dossiersSupervises }}</dd></div> }
                  @if (m.utilisateur.role === 'CLERC' || m.utilisateur.role === 'PRINCIPAL_CLERC') { <div><dt>Dossiers rédigés</dt><dd>{{ m.dossiersRediges }}</dd></div> }
                  <div><dt>Rendez-vous à venir</dt><dd>{{ m.rendezVousAVenir }}</dd></div>
                  <div><dt>Dans l'étude depuis</dt><dd>{{ m.dateEntree | dateFr }}</dd></div>
                </dl>
              </article>
            }
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .membre { background: var(--surface-2); border-radius: 14px; padding: 16px; display: flex; flex-direction: column; gap: 6px; }
    .membre p { margin: 0; }
    .membre__tete { display: flex; gap: 12px; align-items: center; margin-bottom: 4px; }
    .membre__nom { font-weight: 600; font-size: 15px; letter-spacing: -0.01em; margin-bottom: 3px !important; }
    .avatar { width: 40px; height: 40px; font-size: 13px; color: #fff; background: linear-gradient(160deg, #8e8e93, #636366); }
    .avatar--or { background: linear-gradient(160deg, #d9b25c, #a87a22); }
    .membre__chiffres { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 8px 0 0; padding-top: 10px; border-top: 0.5px solid var(--separateur); }
    .membre__chiffres dt { font-size: 12px; color: var(--texte-2); }
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
