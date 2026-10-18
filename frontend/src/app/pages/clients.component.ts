import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, lireErreur } from '../core/api.service';
import { ClientResume, SaisieClient } from '../core/models';
import { SessionService } from '../core/session.service';
import { DateFrPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';
import { Pagination, PaginationComponent } from '../shared/pagination';

/** Fichier clients : chaque personne, avec le nombre de ses dossiers à l'étude. */
@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [FormsModule, RouterLink, DateFrPipe, IconeComponent, PaginationComponent],
  templateUrl: './clients.component.html',
})
export class ClientsComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);
  protected readonly session = inject(SessionService);

  protected readonly clients = signal<ClientResume[]>([]);
  protected readonly recherche = signal('');
  protected readonly type = signal<'' | 'PHYSIQUE' | 'MORALE'>('');
  protected readonly formulaire = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected saisie: SaisieClient = vide();

  protected readonly peutSaisir = computed(() => !this.session.a('ARCHIVISTE', 'FORMALISTE'));
  protected readonly visibles = computed(() => {
    const q = normaliser(this.recherche());
    return this.clients().filter(
      (c) =>
        (!this.type() || c.type === this.type()) &&
        (!q || normaliser([c.nom, c.identifiant ?? '', c.telephone ?? '', c.email ?? ''].join(' ')).includes(q)),
    );
  });

  protected readonly pagination = new Pagination(this.visibles);

  ngOnInit(): void {
    this.api.clients().subscribe((c) => this.clients.set(c));
  }

  protected ouvrir(id: string): void {
    this.router.navigate(['/clients', id]);
  }

  protected creer(): void {
    if (!this.saisie.nom?.trim()) {
      this.erreur.set('Indiquez le nom du client.');
      return;
    }
    this.api.creerClient(this.saisie).subscribe({
      next: (c) => this.router.navigate(['/clients', c.id]),
      error: (e) => this.erreur.set(lireErreur(e).blocages[0]?.message ?? lireErreur(e).message),
    });
  }

  protected annuler(): void {
    this.formulaire.set(false);
    this.saisie = vide();
    this.erreur.set(null);
  }
}

function vide(): SaisieClient {
  return { type: 'PHYSIQUE', nom: '', identifiant: '', telephone: '', email: '', adresse: '' };
}

function normaliser(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
