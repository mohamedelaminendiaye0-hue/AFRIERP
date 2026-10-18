import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LigneMinutier } from '../core/models';
import { DateFrPipe } from '../shared/format.pipes';

/** Repertoire des minutes archivees, tenu par l'archiviste (conservation legale minimale de 75 ans). */
@Component({
  selector: 'app-minutier',
  standalone: true,
  imports: [DateFrPipe],
  template: `
    <div class="page">
      <header class="page__entete">
        <h1>Minutier</h1>
        <p>Repertoire des minutes (actes originaux) archivees a la cloture des dossiers. Duree legale minimale de conservation : 75 ans.</p>
      </header>
      <section class="carte">
        <div class="table-defilante">
          <table>
            <thead>
              <tr><th>Repertoire</th><th>Acte</th><th>Signature</th><th>Notaire signataire</th><th>Responsabilite</th><th>Archive le</th><th>Conservation jusqu'au</th></tr>
            </thead>
            <tbody>
              @for (m of minutes(); track m.numeroRepertoire) {
                <tr class="cliquable" (click)="router.navigate(['/dossiers', m.dossierId])">
                  <td class="ref">{{ m.numeroRepertoire }}</td>
                  <td><span class="ref">{{ m.reference }}</span><br /><span class="tres-petit muet">{{ m.intitule }} · {{ m.typeActeLibelle }}</span></td>
                  <td class="chiffres petit">{{ m.dateSignature | dateFr }}</td>
                  <td class="petit">{{ m.notaireSignataire ?? '—' }}</td>
                  <td class="petit">{{ m.notaireResponsable ?? '—' }}</td>
                  <td class="chiffres petit">{{ m.dateArchivage | dateFr }}</td>
                  <td class="chiffres petit"><span class="badge badge--petit badge--neutre-clair">{{ m.conservationJusquau | dateFr }}</span></td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="vide">Aucune minute archivee.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
})
export class MinutierComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);
  protected readonly minutes = signal<LigneMinutier[]>([]);

  ngOnInit(): void {
    this.api.minutier().subscribe((m) => this.minutes.set(m));
  }
}
