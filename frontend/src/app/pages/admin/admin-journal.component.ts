import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { EvenementAdmin, TypeEvenementAdmin } from '../../core/models';
import { DateFrPipe } from '../../shared/format.pipes';
import { IconeComponent } from '../../shared/icone.component';

/** Journal d'administration : connexions, comptes et réglages ; lecture seule. */
@Component({
  selector: 'app-admin-journal',
  standalone: true,
  imports: [FormsModule, DateFrPipe, IconeComponent],
  template: `
    <div class="page">
      <header class="page__entete entete-ligne">
        <div>
          <p class="surtitre">Administration de la plateforme</p>
          <h1>Journal d'administration</h1>
          <p>Connexions, échecs, gestion des comptes et réglages. Les lignes ne peuvent être ni modifiées ni supprimées.</p>
        </div>
        <button class="bouton bouton--secondaire" type="button" (click)="exporter()"><app-icone nom="telecharger" [taille]="18" />Exporter</button>
      </header>
      <section class="carte">
        <div class="filtres">
          <select class="filtre-domaine" aria-label="Type d'événement" [ngModel]="type()" (ngModelChange)="type.set($event); charger()">
            <option value="">Tous les événements</option>
            @for (t of types(); track t.code) { <option [value]="t.code">{{ t.libelle }}</option> }
          </select>
          <form class="recherche-form" (submit)="$event.preventDefault(); charger()">
            <input class="recherche" type="search" placeholder="Personne, détail, adresse IP…" aria-label="Rechercher dans le journal"
                   [ngModel]="q()" (ngModelChange)="q.set($event)" name="q" />
          </form>
          <span class="muet petit">{{ lignes().length }} événement(s)</span>
        </div>
        <div class="table-defilante">
          <table>
            <thead><tr><th>Date</th><th>Événement</th><th>Compte concerné</th><th>Par</th><th>Détail</th><th>Adresse IP</th></tr></thead>
            <tbody>
              @for (e of lignes(); track e.id) {
                <tr>
                  <td class="chiffres">{{ e.horodatage | dateFr: true }}</td>
                  <td><span class="badge" [class.badge--critique]="e.sensible" [class.badge--neutre-clair]="!e.sensible">{{ e.typeLibelle }}</span></td>
                  <td>{{ e.cibleNom ?? '—' }}</td>
                  <td>{{ e.acteurNom ?? (e.type === 'CONNEXION_ECHOUEE' || e.type === 'COMPTE_VERROUILLE' ? 'Tentative de connexion' : '—') }}</td>
                  <td class="detail">{{ e.detail ?? '' }}</td>
                  <td class="chiffres muet">{{ e.adresseIp ?? '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="vide">Aucun événement ne correspond à ces critères.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .recherche-form { flex: 1 1 260px; max-width: 380px; margin-left: auto; }
    .recherche-form .recherche { max-width: none; width: 100%; margin: 0; }
    .detail { white-space: normal; min-width: 220px; max-width: 420px; color: var(--texte-2); }
    td { white-space: nowrap; }
  `],
})
export class AdminJournalComponent implements OnInit {
  private readonly api = inject(ApiService);

  /** Filtres transmis par les raccourcis (?type=CONNEXION_ECHOUEE, ?q=nom). */
  readonly typeInitial = input<string | undefined>(undefined, { alias: 'type' });
  readonly qInitial = input<string | undefined>(undefined, { alias: 'q' });

  protected readonly types = signal<{ code: TypeEvenementAdmin; libelle: string }[]>([]);
  protected readonly lignes = signal<EvenementAdmin[]>([]);
  protected readonly type = signal<TypeEvenementAdmin | ''>('');
  protected readonly q = signal('');

  ngOnInit(): void {
    if (this.typeInitial()) this.type.set(this.typeInitial() as TypeEvenementAdmin);
    if (this.qInitial()) this.q.set(this.qInitial()!);
    this.api.typesEvenementAdmin().subscribe((t) => this.types.set(t));
    this.charger();
  }

  protected charger(): void {
    this.api.journalAdmin(this.type(), this.q().trim()).subscribe((l) => this.lignes.set(l));
  }

  protected exporter(): void {
    const entetes = ['Date', 'Événement', 'Compte concerné', 'Par', 'Détail', 'Adresse IP'];
    const csv = [entetes, ...this.lignes().map((e) => [e.horodatage, e.typeLibelle, e.cibleNom ?? '', e.acteurNom ?? '', e.detail ?? '', e.adresseIp ?? ''])]
      .map((l) => l.map((x) => `"${x.replace(/"/g, '""')}"`).join(';')).join('\r\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    a.download = 'journal-administration.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }
}
