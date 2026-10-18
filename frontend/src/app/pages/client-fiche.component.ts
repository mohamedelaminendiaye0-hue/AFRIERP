import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, lireErreur } from '../core/api.service';
import { DossierDuClient, FicheClient, SaisieClient } from '../core/models';
import { SessionService } from '../core/session.service';
import { BadgeStatutComponent } from '../shared/badges';
import { DateFrPipe, XofPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';

type Filtre = 'tous' | 'en-cours' | 'clotures';

/** Fiche client : identité, coordonnées et historique complet des dossiers. */
@Component({
  selector: 'app-client-fiche',
  standalone: true,
  imports: [FormsModule, RouterLink, BadgeStatutComponent, DateFrPipe, XofPipe, IconeComponent],
  templateUrl: './client-fiche.component.html',
  styleUrl: './client-fiche.component.scss',
})
export class ClientFicheComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  readonly id = input.required<string>();

  protected readonly fiche = signal<FicheClient | null>(null);
  protected readonly filtre = signal<Filtre>('tous');
  protected readonly edition = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected saisie: SaisieClient = { type: 'PHYSIQUE', nom: '' };

  protected readonly peutSaisir = computed(() => !this.session.a('ARCHIVISTE', 'FORMALISTE'));
  protected readonly peutOuvrirDossier = computed(() => !this.session.a('ARCHIVISTE', 'FORMALISTE', 'COMPTABLE_TAXATEUR'));
  protected readonly dossiers = computed(() => {
    const ds = this.fiche()?.dossiers ?? [];
    switch (this.filtre()) {
      case 'en-cours':
        return ds.filter((d) => d.statut !== 'CLOSED');
      case 'clotures':
        return ds.filter((d) => d.statut === 'CLOSED');
      default:
        return ds;
    }
  });
  protected readonly initiales = computed(() =>
    (this.fiche()?.client.nom ?? '')
      .replace(/^(M\.|Mme|Mlle)\s+/, '')
      .split(/\s+/)
      .slice(0, 2)
      .map((m) => m.charAt(0))
      .join('')
      .toUpperCase(),
  );

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.api.ficheClient(this.id()).subscribe((f) => this.fiche.set(f));
  }

  protected modifier(): void {
    const c = this.fiche()!.client;
    this.saisie = { type: c.type, nom: c.nom, identifiant: c.identifiant, telephone: c.telephone, email: c.email,
      adresse: c.adresse, regimeMatrimonial: c.regimeMatrimonial, notes: c.notes };
    this.erreur.set(null);
    this.edition.set(true);
  }

  protected enregistrer(): void {
    this.api.modifierClient(this.id(), this.saisie).subscribe({
      next: () => {
        this.edition.set(false);
        this.charger();
      },
      error: (e) => this.erreur.set(lireErreur(e).blocages[0]?.message ?? lireErreur(e).message),
    });
  }

  protected annee(d: DossierDuClient): string {
    return (d.dateSignature ?? d.dateOuverture).slice(0, 4);
  }
}
