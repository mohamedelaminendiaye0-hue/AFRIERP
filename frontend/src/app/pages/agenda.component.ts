import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { ApiService, NouveauRendezVous, lireErreur } from '../core/api.service';
import { Blocage, DossierResume, ErreurApi, ReferentielsAgenda, RendezVous, Salle, StatutRendezVous, TypeRendezVous } from '../core/models';
import { SessionService } from '../core/session.service';
import { DateFrPipe } from '../shared/format.pipes';

const PX_PAR_MINUTE = 1.1;
const JOURS = ['Dimanche', 'Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi'];

/** Date locale au format ISO sans fuseau (le backend manipule des LocalDate / LocalDateTime). */
function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function lundiDe(d: Date): Date {
  const r = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const decalage = (r.getDay() + 6) % 7;
  r.setDate(r.getDate() - decalage);
  return r;
}

/** Semaine de travail a afficher : le week-end, celle qui commence lundi prochain. */
function semaineCourante(): Date {
  const aujourdhui = new Date();
  const lundi = lundiDe(aujourdhui);
  if (aujourdhui.getDay() === 0 || aujourdhui.getDay() === 6) lundi.setDate(lundi.getDate() + 7);
  return lundi;
}

function minutes(heure: string): number {
  const [h, m] = heure.split(':').map(Number);
  return h * 60 + m;
}

interface Bloc {
  rdv: RendezVous;
  haut: number;
  hauteur: number;
  couloir: number;
  couloirs: number;
}

@Component({
  selector: 'app-agenda',
  standalone: true,
  imports: [FormsModule, RouterLink, DateFrPipe],
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.scss',
})
export class AgendaComponent implements OnInit {
  /** Pre-remplissage depuis un dossier : /agenda?dossier=D-0112&type=SIGNATURE */
  readonly dossier = input<string>();
  readonly type = input<string>();

  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);

  protected readonly referentiels = signal<ReferentielsAgenda | null>(null);
  protected readonly rendezVous = signal<RendezVous[]>([]);
  protected readonly dossiers = signal<DossierResume[]>([]);
  protected readonly lundi = signal(semaineCourante());
  protected readonly selection = signal<RendezVous | null>(null);
  protected readonly blocages = signal<Blocage[]>([]);
  protected readonly erreur = signal<ErreurApi | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly formulaireOuvert = signal(false);

  protected filtrePersonne = '';
  protected filtreSalle = '';
  protected masquerClos = true;
  protected readonly filtres = signal(0); // incremente pour recalculer les blocs

  protected form = this.formulaireVide();
  protected reportDate = '';
  protected motifAnnulation = '';

  private readonly verification$ = new Subject<NouveauRendezVous>();

  protected readonly heures = computed(() => {
    const r = this.referentiels();
    if (!r) return [];
    const debut = minutes(r.ouverture) / 60;
    const fin = minutes(r.fermeture) / 60;
    return Array.from({ length: fin - debut }, (_, i) => debut + i);
  });

  protected readonly hauteurGrille = computed(() => {
    const r = this.referentiels();
    return r ? (minutes(r.fermeture) - minutes(r.ouverture)) * PX_PAR_MINUTE : 0;
  });

  protected readonly jours = computed(() => {
    const r = this.referentiels();
    const ouvres = r?.joursOuvres ?? [1, 2, 3, 4, 5];
    return ouvres.map((n) => {
      const d = new Date(this.lundi());
      d.setDate(d.getDate() + n - 1);
      return { date: isoDate(d), libelle: `${JOURS[d.getDay()]} ${d.getDate()}/${d.getMonth() + 1}`, aujourdhui: isoDate(d) === isoDate(new Date()) };
    });
  });

  protected readonly personnes = computed(() => this.session.utilisateurs().filter((u) => u.actif));
  protected readonly notaires = computed(() => this.session.utilisateurs().filter((u) => u.notaire && u.actif));
  protected readonly collaborateurs = computed(() => this.session.utilisateurs().filter((u) => !u.notaire && u.actif));
  protected readonly dossiersProposes = computed(() =>
    this.dossiers().filter((d) => d.statut !== 'CLOSED' && (this.form.type !== 'SIGNATURE' || d.statut === 'READY_TO_SIGN')),
  );

  protected readonly blocsParJour = computed(() => {
    this.filtres();
    const r = this.referentiels();
    const resultat: Record<string, Bloc[] | undefined> = {};
    if (!r) return resultat;
    const ouverture = minutes(r.ouverture);
    for (const jour of this.jours()) {
      const liste = this.rendezVous()
        .filter((x) => x.debut.startsWith(jour.date))
        .filter((x) => !this.masquerClos || x.statut === 'PLANIFIE' || x.statut === 'CONFIRME' || x.statut === 'HONORE')
        .filter((x) => !this.filtrePersonne || x.notaireId === this.filtrePersonne || x.collaborateurId === this.filtrePersonne)
        .filter((x) => !this.filtreSalle || x.salle === this.filtreSalle)
        .sort((a, b) => a.debut.localeCompare(b.debut));
      // Repartition en couloirs par groupe de rendez-vous qui se chevauchent :
      // seuls les rendez-vous simultanes partagent la largeur de la colonne.
      const blocs: Bloc[] = [];
      let groupe: Bloc[] = [];
      let finsCouloirs: string[] = [];
      let finGroupe = '';
      const fermer = () => groupe.forEach((b) => (b.couloirs = finsCouloirs.length));
      for (const rdv of liste) {
        if (groupe.length && rdv.debut >= finGroupe) {
          fermer();
          groupe = [];
          finsCouloirs = [];
        }
        let couloir = finsCouloirs.findIndex((fin) => fin <= rdv.debut);
        if (couloir === -1) couloir = finsCouloirs.push(rdv.fin) - 1;
        else finsCouloirs[couloir] = rdv.fin;
        finGroupe = groupe.length && finGroupe > rdv.fin ? finGroupe : rdv.fin;
        const bloc: Bloc = {
          rdv,
          haut: (minutes(rdv.debut.slice(11, 16)) - ouverture) * PX_PAR_MINUTE,
          hauteur: Math.max(22, rdv.dureeMinutes * PX_PAR_MINUTE - 2),
          couloir,
          couloirs: 1,
        };
        groupe.push(bloc);
        blocs.push(bloc);
      }
      fermer();
      resultat[jour.date] = blocs;
    }
    return resultat;
  });

  ngOnInit(): void {
    this.api.referentielsAgenda().subscribe((r) => this.referentiels.set(r));
    this.api.dossiers().subscribe((d) => {
      this.dossiers.set(d);
      const id = this.dossier();
      if (id) this.prefillDepuisDossier(id);
    });
    this.verification$
      .pipe(debounceTime(250), switchMap((cmd) => this.api.verifierRendezVous(cmd)))
      .subscribe({ next: (b) => this.blocages.set(b), error: () => this.blocages.set([]) });
    this.charger();
  }

  /* --- Navigation --- */

  protected semaine(decalage: number): void {
    const d = new Date(this.lundi());
    d.setDate(d.getDate() + 7 * decalage);
    this.lundi.set(decalage === 0 ? semaineCourante() : d);
    this.charger();
  }

  protected charger(): void {
    const vendredi = new Date(this.lundi());
    vendredi.setDate(vendredi.getDate() + 6);
    this.api.agenda(isoDate(this.lundi()), isoDate(vendredi)).subscribe((r) => {
      this.rendezVous.set(r);
      const sel = this.selection();
      if (sel) this.selection.set(r.find((x) => x.id === sel.id) ?? null);
    });
  }

  protected rafraichirFiltres(): void {
    this.filtres.update((n) => n + 1);
  }

  /* --- Saisie --- */

  private formulaireVide() {
    return {
      type: 'CONSULTATION' as TypeRendezVous,
      date: '',
      heure: '09:00',
      duree: 45,
      salle: 'BUREAU_NOTAIRE' as Salle,
      notaireId: '',
      collaborateurId: '',
      dossierId: '',
      clientNom: '',
      clientTelephone: '',
      objet: '',
    };
  }

  private prefillDepuisDossier(id: string): void {
    const d = this.dossiers().find((x) => x.id === id);
    if (!d) return;
    this.form = this.formulaireVide();
    this.form.dossierId = d.id;
    this.form.notaireId = d.notaireId;
    this.form.collaborateurId = d.clercId ?? '';
    if (this.type() === 'SIGNATURE') {
      this.form.type = 'SIGNATURE';
      this.form.duree = 90;
      this.form.salle = 'SALLE_SIGNATURES';
      this.form.objet = `Signature — ${d.intitule}`;
    }
    this.form.date = this.prochainJourOuvre();
    this.formulaireOuvert.set(true);
    this.verifier();
  }

  private prochainJourOuvre(): string {
    const d = new Date();
    do {
      d.setDate(d.getDate() + 1);
    } while (d.getDay() === 0 || d.getDay() === 6);
    return isoDate(d);
  }

  protected nouveauSurCreneau(date: string, evenement: MouseEvent): void {
    const r = this.referentiels();
    if (!r || (evenement.target as HTMLElement).closest('.rdv')) return;
    const y = evenement.offsetY;
    const total = minutes(r.ouverture) + Math.floor(y / PX_PAR_MINUTE / 30) * 30;
    this.form = { ...this.formulaireVide(), date, heure: `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}` };
    if (this.session.utilisateur()?.notaire) this.form.notaireId = this.session.id();
    this.selection.set(null);
    this.formulaireOuvert.set(true);
    this.verifier();
  }

  protected ouvrirFormulaire(): void {
    this.form = { ...this.formulaireVide(), date: this.prochainJourOuvre() };
    this.selection.set(null);
    this.formulaireOuvert.set(true);
    this.verifier();
  }

  protected changerType(): void {
    const t = this.referentiels()?.types.find((x) => x.code === this.form.type);
    if (t) this.form.duree = t.dureeParDefaut;
    if (this.form.type === 'SIGNATURE') this.form.salle = 'SALLE_SIGNATURES';
    this.verifier();
  }

  protected choisirDossier(): void {
    const d = this.dossiers().find((x) => x.id === this.form.dossierId);
    if (d) {
      this.form.notaireId = this.form.notaireId || d.notaireId;
      this.form.collaborateurId = this.form.collaborateurId || (d.clercId ?? '');
    }
    this.verifier();
  }

  private commande(): NouveauRendezVous {
    const f = this.form;
    return {
      type: f.type,
      debut: `${f.date}T${f.heure}:00`,
      dureeMinutes: Number(f.duree),
      salle: f.salle,
      notaireId: f.notaireId || null,
      collaborateurId: f.collaborateurId || null,
      dossierId: f.dossierId || null,
      clientNom: f.clientNom || null,
      clientTelephone: f.clientTelephone || null,
      objet: f.objet || null,
    };
  }

  protected verifier(): void {
    if (this.form.date && this.form.heure) this.verification$.next(this.commande());
  }

  protected creer(): void {
    this.erreur.set(null);
    this.api.creerRendezVous(this.commande()).subscribe({
      next: (r) => {
        this.message.set(`Rendez-vous planifié : ${r.typeLibelle} le ${new Date(r.debut).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })}.`);
        this.formulaireOuvert.set(false);
        this.lundi.set(lundiDe(new Date(r.debut)));
        this.charger();
        this.selection.set(r);
      },
      error: (e) => this.erreur.set(lireErreur(e)),
    });
  }

  /* --- Cycle du rendez-vous --- */

  protected selectionner(r: RendezVous): void {
    this.selection.set(r);
    this.formulaireOuvert.set(false);
    this.erreur.set(null);
    this.reportDate = r.debut.slice(0, 16);
    this.motifAnnulation = '';
  }

  protected estPasse(r: RendezVous): boolean {
    return new Date(r.debut) <= new Date();
  }

  protected actif(r: RendezVous): boolean {
    return r.statut === 'PLANIFIE' || r.statut === 'CONFIRME';
  }

  protected changerStatut(r: RendezVous, statut: StatutRendezVous): void {
    this.erreur.set(null);
    this.api.statutRendezVous(r.id, statut, statut === 'ANNULE' ? this.motifAnnulation : undefined).subscribe({
      next: (maj) => {
        this.message.set(`Rendez-vous ${maj.statutLibelle.toLowerCase()}.`);
        this.selection.set(maj);
        this.charger();
      },
      error: (e) => this.erreur.set(lireErreur(e)),
    });
  }

  protected reporter(r: RendezVous): void {
    this.erreur.set(null);
    this.api.reporterRendezVous(r.id, `${this.reportDate}:00`).subscribe({
      next: (maj) => {
        this.message.set('Rendez-vous reporté.');
        this.lundi.set(lundiDe(new Date(maj.debut)));
        this.selection.set(maj);
        this.charger();
      },
      error: (e) => this.erreur.set(lireErreur(e)),
    });
  }

  protected libelleSemaine(): string {
    const fin = new Date(this.lundi());
    fin.setDate(fin.getDate() + 4);
    return `${this.lundi().toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' })} — ${fin.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })}`;
  }

  protected heure(iso: string): string {
    return iso.slice(11, 16);
  }
}
