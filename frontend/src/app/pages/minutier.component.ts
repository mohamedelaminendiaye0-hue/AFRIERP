import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LigneMinutier, ResultatArchivage } from '../core/models';
import { SessionService } from '../core/session.service';
import { ConfirmationComponent } from '../shared/confirmation.component';
import { DateFrPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';

type Etat = LigneMinutier['etat'] | 'TOUTES';

const ETATS: { cle: Etat; libelle: string }[] = [
  { cle: 'A_ARCHIVER', libelle: 'À archiver' },
  { cle: 'FORMALITES', libelle: 'Formalités en cours' },
  { cle: 'ARCHIVEE', libelle: 'Archivées' },
  { cle: 'TOUTES', libelle: 'Toutes' },
];

/**
 * Minutier : conservation des minutes signées (75 ans au minimum). L'archivage
 * range la minute à son emplacement physique et clôture le dossier ; il se fait
 * minute par minute ou par lot (liasse).
 */
@Component({
  selector: 'app-minutier',
  standalone: true,
  imports: [DateFrPipe, FormsModule, RouterLink, ConfirmationComponent, IconeComponent],
  templateUrl: './minutier.component.html',
})
export class MinutierComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  /** Filtre transmis par les raccourcis (?etat=A_ARCHIVER). */
  readonly etat = input<string>();

  protected readonly etats = ETATS;
  protected readonly minutes = signal<LigneMinutier[]>([]);
  protected readonly onglet = signal<Etat>('A_ARCHIVER');
  protected readonly selection = signal<Set<string>>(new Set());
  protected readonly confirmation = signal<string[] | null>(null);
  protected readonly occupe = signal(false);
  protected readonly resultats = signal<ResultatArchivage[] | null>(null);
  protected emplacement = '';

  protected readonly peutArchiver = computed(() => this.session.a('ARCHIVISTE', 'NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE'));
  protected readonly visibles = computed(() => {
    const o = this.onglet();
    return o === 'TOUTES' ? this.minutes() : this.minutes().filter((m) => m.etat === o);
  });
  protected readonly compte = (e: Etat) => (e === 'TOUTES' ? this.minutes().length : this.minutes().filter((m) => m.etat === e).length);
  protected readonly archivablesSelectionnes = computed(() =>
    this.minutes().filter((m) => this.selection().has(m.dossierId) && m.etat === 'A_ARCHIVER'),
  );
  protected readonly toutSelectionne = computed(() => {
    const a = this.visibles().filter((m) => m.etat === 'A_ARCHIVER');
    return a.length > 0 && a.every((m) => this.selection().has(m.dossierId));
  });
  protected readonly anneeCourante = new Date().getFullYear();
  protected readonly inscritesAnnee = computed(() => this.minutes().filter((m) => m.numeroRepertoire.startsWith(`${this.anneeCourante}/`)).length);

  ngOnInit(): void {
    const e = this.etat();
    if (e && ETATS.some((x) => x.cle === e)) this.onglet.set(e as Etat);
    this.charger();
  }

  protected basculer(id: string): void {
    const s = new Set(this.selection());
    if (s.has(id)) s.delete(id);
    else s.add(id);
    this.selection.set(s);
  }

  protected basculerTout(): void {
    const a = this.visibles().filter((m) => m.etat === 'A_ARCHIVER').map((m) => m.dossierId);
    this.selection.set(this.toutSelectionne() ? new Set() : new Set(a));
  }

  protected readonly refus = computed(() => (this.resultats() ?? []).filter((x) => !x.archive));

  protected demanderSelection(): void {
    this.demander(this.archivablesSelectionnes().map((m) => m.dossierId));
  }

  protected demander(ids: string[]): void {
    this.resultats.set(null);
    this.emplacement = this.emplacement || this.liasseParDefaut();
    this.confirmation.set(ids);
  }

  protected archiver(): void {
    const ids = this.confirmation();
    if (!ids) return;
    this.occupe.set(true);
    this.api.archiver(ids, this.emplacement).subscribe({
      next: (r) => {
        this.occupe.set(false);
        this.confirmation.set(null);
        this.resultats.set(r);
        this.selection.set(new Set());
        this.charger();
      },
      error: () => {
        this.occupe.set(false);
        this.confirmation.set(null);
        this.resultats.set(ids.map((id) => ({ dossierId: id, archive: false, numeroRepertoire: null, blocages: [{ code: 'ERREUR', message: "Le serveur n'a pas pu traiter la demande." }] })));
      },
    });
  }

  protected recap(): [string, string][] {
    const ids = this.confirmation() ?? [];
    const lignes = this.minutes().filter((m) => ids.includes(m.dossierId));
    if (lignes.length === 1) {
      return [
        ['Minute', `n° ${lignes[0].numeroRepertoire}`],
        ['Acte', lignes[0].intitule],
        ['Conservation jusqu’au', lignes[0].conservationJusquau ? new Date(lignes[0].conservationJusquau).toLocaleDateString('fr-FR') : '—'],
      ];
    }
    return [['Minutes', `${lignes.length} minutes`], ['Numéros', lignes.map((l) => l.numeroRepertoire).join(', ')]];
  }

  protected reference(id: string): string {
    return this.minutes().find((m) => m.dossierId === id)?.numeroRepertoire ?? id;
  }

  private liasseParDefaut(): string {
    const d = new Date();
    return `Liasse ${d.getFullYear()}-T${Math.floor(d.getMonth() / 3) + 1}`;
  }

  private charger(): void {
    this.api.minutier().subscribe((m) => this.minutes.set(m));
  }
}
