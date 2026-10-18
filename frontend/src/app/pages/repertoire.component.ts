import { LowerCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, lireErreur } from '../core/api.service';
import { IdentiteService } from '../core/identite.service';
import { EtatArchivage, LigneRepertoire, Preuve, Repertoire, ResultatArchivage, TypePreuve } from '../core/models';
import { SessionService } from '../core/session.service';
import { ApercuPreuveComponent } from '../shared/apercu-preuve.component';
import { ConfirmationComponent } from '../shared/confirmation.component';
import { DateFrPipe, XofPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';
import { Pagination, PaginationComponent } from '../shared/pagination';

type Onglet = EtatArchivage | 'TOUS';

const ONGLETS: { cle: Onglet; libelle: string }[] = [
  { cle: 'TOUS', libelle: 'Tous les actes' },
  { cle: 'A_ARCHIVER', libelle: 'À archiver' },
  { cle: 'FORMALITES', libelle: 'Formalités en cours' },
  { cle: 'ARCHIVEE', libelle: 'Archivés' },
];

/**
 * Répertoire des actes : registre légal tenu dans l'ordre chronologique, et
 * archives de l'étude. Chaque acte signé y reçoit un numéro d'ordre définitif,
 * ses preuves (minute numérisée, quittance…) et, une fois les formalités
 * achevées, son archivage à un emplacement physique.
 */
@Component({
  selector: 'app-repertoire',
  standalone: true,
  imports: [RouterLink, FormsModule, LowerCasePipe, DateFrPipe, XofPipe, IconeComponent, ConfirmationComponent,
    ApercuPreuveComponent, PaginationComponent],
  templateUrl: './repertoire.component.html',
  styleUrl: './repertoire.component.scss',
})
export class RepertoireComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);
  protected readonly identite = inject(IdentiteService).identite;

  /** Paramètres d'adresse : ?etat=A_ARCHIVER, ?acte=D-0082. */
  readonly etat = input<string>();
  readonly acte = input<string>();

  protected readonly onglets = ONGLETS;
  protected readonly repertoire = signal<Repertoire | null>(null);
  protected readonly onglet = signal<Onglet>('TOUS');
  protected readonly recherche = signal('');
  protected readonly nature = signal('');
  protected readonly mois = signal('');
  protected readonly selection = signal<Set<string>>(new Set());
  protected readonly ouvert = signal<string | null>(null);

  /* Archivage */
  protected readonly confirmation = signal<string[] | null>(null);
  protected readonly occupe = signal(false);
  protected readonly resultats = signal<ResultatArchivage[] | null>(null);
  protected emplacement = '';

  /* Preuves */
  protected readonly preuves = signal<Preuve[]>([]);
  protected readonly typesPreuve = signal<{ code: TypePreuve; libelle: string }[]>([]);
  protected readonly apercu = signal<Preuve | null>(null);
  protected readonly envoi = signal(false);
  protected readonly erreurPreuve = signal<string | null>(null);
  protected fichier: File | null = null;
  protected typePreuve: TypePreuve = 'MINUTE_SIGNEE';
  protected commentairePreuve = '';

  protected readonly peutArchiver = computed(() => this.session.a('ARCHIVISTE', 'NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE'));
  protected readonly peutVerser = computed(() =>
    this.session.a('NOTAIRE_TITULAIRE', 'NOTAIRE_ASSOCIE', 'NOTAIRE_SALARIE', 'PRINCIPAL_CLERC', 'COMPTABLE_TAXATEUR', 'FORMALISTE', 'ARCHIVISTE'),
  );

  protected readonly toutes = computed(() => this.repertoire()?.lignes ?? []);
  protected readonly natures = computed(() => [...new Set(this.toutes().map((l) => l.nature))].sort());
  protected readonly moisDisponibles = computed(() =>
    [...new Set(this.toutes().map((l) => l.date?.slice(0, 7)).filter(Boolean) as string[])].sort().reverse(),
  );
  protected readonly lignes = computed(() => {
    const q = normaliser(this.recherche());
    return this.toutes().filter(
      (l) =>
        (this.onglet() === 'TOUS' || l.etat === this.onglet()) &&
        (!this.nature() || l.nature === this.nature()) &&
        (!this.mois() || l.date?.startsWith(this.mois())) &&
        (!q || normaliser([l.numero, l.nature, l.dossierId, l.intitule, l.emplacement ?? '', ...l.parties.map((p) => p.nom)].join(' ')).includes(q)),
    );
  });
  protected readonly pagination = new Pagination(this.lignes);
  protected readonly compte = (o: Onglet) => (o === 'TOUS' ? this.toutes().length : this.toutes().filter((l) => l.etat === o).length);
  protected readonly sansPreuve = computed(() => this.toutes().filter((l) => l.nbPreuves === 0).length);
  protected readonly trimestreCourant = computed(() => {
    const t = this.repertoire()?.trimestres ?? [];
    return t.length ? t[t.length - 1] : null;
  });
  protected readonly acteOuvert = computed(() => this.toutes().find((l) => l.dossierId === this.ouvert()) ?? null);
  protected readonly archivablesSelectionnes = computed(() =>
    this.toutes().filter((l) => this.selection().has(l.dossierId) && l.etat === 'A_ARCHIVER'),
  );
  protected readonly toutSelectionne = computed(() => {
    const a = this.pagination.elements().filter((l) => l.etat === 'A_ARCHIVER');
    return a.length > 0 && a.every((l) => this.selection().has(l.dossierId));
  });
  protected readonly refus = computed(() => (this.resultats() ?? []).filter((x) => !x.archive));

  ngOnInit(): void {
    const e = this.etat();
    if (e && ONGLETS.some((o) => o.cle === e)) this.onglet.set(e as Onglet);
    this.api.typesPreuve().subscribe((t) => this.typesPreuve.set(t));
    this.charger(undefined, () => {
      if (this.acte()) this.ouvrir(this.acte()!);
    });
  }

  protected charger(annee?: number, ensuite?: () => void): void {
    this.api.repertoire(annee ?? this.repertoire()?.annee).subscribe((r) => {
      this.repertoire.set(r);
      ensuite?.();
    });
  }

  protected changerOnglet(o: Onglet): void {
    this.onglet.set(o);
    this.pagination.reinitialiser();
  }

  /* --- Panneau de l'acte et preuves --- */

  protected ouvrir(dossierId: string): void {
    this.ouvert.set(dossierId);
    this.erreurPreuve.set(null);
    this.fichier = null;
    this.commentairePreuve = '';
    this.preuves.set([]);
    this.api.preuves(dossierId).subscribe((p) => {
      this.preuves.set(p);
      this.typePreuve = p.some((x) => x.type === 'MINUTE_SIGNEE') ? 'QUITTANCE_ENREGISTREMENT' : 'MINUTE_SIGNEE';
    });
  }

  protected choisirFichier(e: Event): void {
    this.fichier = (e.target as HTMLInputElement).files?.[0] ?? null;
    this.erreurPreuve.set(null);
    if (this.fichier && this.fichier.size > 15 * 1024 * 1024) {
      this.erreurPreuve.set('Le fichier dépasse 15 Mo.');
      this.fichier = null;
    }
  }

  protected verser(champ: HTMLInputElement): void {
    const id = this.ouvert();
    if (!id || !this.fichier) return;
    this.envoi.set(true);
    this.erreurPreuve.set(null);
    this.api.verserPreuve(id, this.fichier, this.typePreuve, this.commentairePreuve).subscribe({
      next: (p) => {
        this.envoi.set(false);
        this.preuves.update((liste) => [p, ...liste]);
        this.fichier = null;
        this.commentairePreuve = '';
        champ.value = '';
        this.charger();
      },
      error: (e) => {
        this.envoi.set(false);
        const err = lireErreur(e);
        this.erreurPreuve.set(e?.status === 413 ? 'Le fichier dépasse 15 Mo.' : err.blocages[0]?.message ?? err.message);
      },
    });
  }

  protected taille(o: number): string {
    return o >= 1024 * 1024 ? `${(o / 1024 / 1024).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo` : `${Math.max(1, Math.round(o / 1024))} Ko`;
  }

  /* --- Archivage --- */

  protected basculer(id: string): void {
    const s = new Set(this.selection());
    if (s.has(id)) s.delete(id);
    else s.add(id);
    this.selection.set(s);
  }

  protected basculerTout(): void {
    const a = this.pagination.elements().filter((l) => l.etat === 'A_ARCHIVER').map((l) => l.dossierId);
    const s = new Set(this.selection());
    if (this.toutSelectionne()) a.forEach((id) => s.delete(id));
    else a.forEach((id) => s.add(id));
    this.selection.set(s);
  }

  protected demander(ids: string[]): void {
    this.resultats.set(null);
    if (!this.emplacement) {
      const d = new Date();
      this.emplacement = `Liasse ${d.getFullYear()}-T${Math.floor(d.getMonth() / 3) + 1}`;
    }
    this.confirmation.set(ids);
  }

  protected demanderSelection(): void {
    this.demander(this.archivablesSelectionnes().map((l) => l.dossierId));
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
        this.resultats.set(ids.map((id) => ({ dossierId: id, archive: false, numeroRepertoire: null,
          blocages: [{ code: 'ERREUR', message: "Le serveur n'a pas pu traiter la demande." }] })));
      },
    });
  }

  protected recap(): [string, string][] {
    const ids = this.confirmation() ?? [];
    const lignes = this.toutes().filter((l) => ids.includes(l.dossierId));
    if (lignes.length === 1) {
      return [['Acte', `n° ${lignes[0].numero} · ${lignes[0].nature}`], ['Intitulé', lignes[0].intitule],
        ['Preuves jointes', String(lignes[0].nbPreuves)]];
    }
    return [['Actes', `${lignes.length} actes`], ['Numéros', lignes.map((l) => l.numero).join(', ')]];
  }

  protected numero(id: string): string {
    return this.toutes().find((l) => l.dossierId === id)?.numero ?? id;
  }

  /* --- Export et impression --- */

  protected libelleMois(m: string): string {
    return new Date(`${m}-01T00:00:00`).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  }

  protected imprimer(): void {
    window.print();
  }

  protected exporter(): void {
    const entetes = ["N° d'ordre", 'Date', "Nature de l'acte", 'Forme', 'Parties', 'Enregistrement', 'Droits perçus (FCFA)', 'Notaire', 'État', 'Emplacement', 'Preuves'];
    const etat: Record<EtatArchivage, string> = { ARCHIVEE: 'Archivé', A_ARCHIVER: 'À archiver', FORMALITES: 'Formalités en cours' };
    const cellules = (l: LigneRepertoire) => [
      l.numero, l.date ?? '', l.nature, l.forme, l.parties.map((p) => `${p.nom} (${p.qualite})`).join(' ; '),
      l.enregistrement ? `${l.enregistrement.reference ?? ''} ${l.enregistrement.dateAchevement ?? ''}`.trim() : 'À enregistrer',
      l.droitsEnregistrement?.toString() ?? '', l.notaire ?? '', etat[l.etat], l.emplacement ?? '', String(l.nbPreuves),
    ];
    const csv = [entetes, ...this.lignes().map(cellules)].map((ligne) => ligne.map((c) => `"${c.replace(/"/g, '""')}"`).join(';')).join('\r\n');
    const lien = document.createElement('a');
    lien.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    lien.download = `repertoire-${this.repertoire()?.annee}.csv`;
    lien.click();
    URL.revokeObjectURL(lien.href);
  }
}

function normaliser(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
