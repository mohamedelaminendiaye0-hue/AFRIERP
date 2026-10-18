import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, lireErreur } from '../../core/api.service';
import { ParametresSecurite } from '../../core/models';
import { DateFrPipe } from '../../shared/format.pipes';
import { IconeComponent } from '../../shared/icone.component';

/** Politique de sécurité de la plateforme : mots de passe, sessions, verrouillage. */
@Component({
  selector: 'app-admin-securite',
  standalone: true,
  imports: [FormsModule, DateFrPipe, IconeComponent],
  template: `
    <div class="page">
      <header class="page__entete entete-ligne">
        <div>
          <p class="surtitre">Administration de la plateforme</p>
          <h1>Politique de sécurité</h1>
          <p>Règles appliquées à tous les comptes de l'étude. Chaque modification est tracée au journal d'administration.</p>
        </div>
        <div class="actions">
          <button class="bouton bouton--fantome" type="button" (click)="charger()">Annuler les modifications</button>
          <button class="bouton" type="button" [disabled]="envoi() || !p()" (click)="enregistrer()">{{ envoi() ? 'Enregistrement…' : 'Enregistrer la politique' }}</button>
        </div>
      </header>

      @if (message(); as m) { <div class="bandeau" [class.bandeau--bon]="m.ok" [class.bandeau--critique]="!m.ok" role="status">{{ m.texte }}</div> }

      @if (p(); as v) {
        <div class="grille grille--2">
          <section class="carte">
            <div class="carte__entete"><div><h2>Mots de passe</h2><p>Exigences pour les mots de passe choisis par les utilisateurs.</p></div></div>
            <div class="carte__corps">
              <div class="champ">
                <label for="s-long">Longueur minimale</label>
                <div class="curseur">
                  <input id="s-long" type="range" min="8" max="24" [ngModel]="v.longueurMinimale" (ngModelChange)="maj('longueurMinimale', +$event)" />
                  <strong class="chiffres">{{ v.longueurMinimale }} caractères</strong>
                </div>
                <p class="champ__aide">Lettres et chiffres sont toujours exigés ; le mot de passe ne peut pas reprendre l'identifiant.</p>
              </div>
              <label class="interrupteur">
                <input type="checkbox" [ngModel]="v.changementObligatoire" (ngModelChange)="maj('changementObligatoire', $event)" />
                <span><strong>Mot de passe provisoire à la création</strong>
                  <span class="sous">Un compte créé doit choisir son mot de passe à la première connexion. (Une réinitialisation l'impose toujours.)</span></span>
              </label>
            </div>
          </section>

          <section class="carte">
            <div class="carte__entete"><div><h2>Sessions</h2><p>Durée pendant laquelle une connexion reste ouverte.</p></div></div>
            <div class="carte__corps">
              <div class="champ">
                <label for="s-session">Durée de session</label>
                <div class="curseur">
                  <input id="s-session" type="range" min="1" max="24" [ngModel]="v.dureeSessionHeures" (ngModelChange)="maj('dureeSessionHeures', +$event)" />
                  <strong class="chiffres">{{ v.dureeSessionHeures }} h</strong>
                </div>
                <p class="champ__aide">Au-delà, l'utilisateur doit se reconnecter. Une journée de travail à l'étude dure environ 10 h.</p>
              </div>
            </div>
          </section>

          <section class="carte span-2">
            <div class="carte__entete"><div><h2>Protection contre les tentatives répétées</h2><p>Verrouillage temporaire après plusieurs échecs de connexion consécutifs.</p></div></div>
            <div class="carte__corps grille grille--2">
              <div class="champ">
                <label for="s-essais">Échecs avant verrouillage</label>
                <div class="curseur">
                  <input id="s-essais" type="range" min="3" max="10" [ngModel]="v.tentativesAvantVerrouillage" (ngModelChange)="maj('tentativesAvantVerrouillage', +$event)" />
                  <strong class="chiffres">{{ v.tentativesAvantVerrouillage }} essais</strong>
                </div>
              </div>
              <div class="champ">
                <label for="s-verrou">Durée du verrouillage</label>
                <select id="s-verrou" [ngModel]="v.dureeVerrouillageMinutes" (ngModelChange)="maj('dureeVerrouillageMinutes', +$event)">
                  @for (d of durees; track d[0]) { <option [ngValue]="d[0]">{{ d[1] }}</option> }
                </select>
                <p class="champ__aide">Le super administrateur peut déverrouiller un compte à tout moment.</p>
              </div>
            </div>
          </section>
        </div>
        @if (v.miseAJourLe) { <p class="muet petit">Dernière modification le {{ v.miseAJourLe | dateFr: true }} par {{ v.miseAJourPar }}.</p> }
      }
    </div>
  `,
  styles: [`
    .curseur { display: flex; align-items: center; gap: 16px; }
    .curseur input { flex: 1; min-height: 0; padding: 0; border: 0; box-shadow: none; accent-color: var(--primaire); }
    .curseur strong { min-width: 110px; text-align: right; }
    .interrupteur { display: flex; gap: 12px; align-items: flex-start; font-weight: 400; cursor: pointer; padding-top: 6px; }
    .interrupteur input { margin-top: 3px; }
  `],
})
export class AdminSecuriteComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly p = signal<ParametresSecurite | null>(null);
  protected readonly envoi = signal(false);
  protected readonly message = signal<{ ok: boolean; texte: string } | null>(null);
  protected readonly durees: [number, string][] = [[5, '5 minutes'], [15, '15 minutes'], [30, '30 minutes'], [60, '1 heure'], [240, '4 heures'], [1440, '24 heures']];

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.message.set(null);
    this.api.parametresSecurite().subscribe((p) => this.p.set(p));
  }

  protected maj<K extends keyof ParametresSecurite>(cle: K, valeur: ParametresSecurite[K]): void {
    this.p.update((p) => (p ? { ...p, [cle]: valeur } : p));
    this.message.set(null);
  }

  protected enregistrer(): void {
    const p = this.p();
    if (!p) return;
    this.envoi.set(true);
    this.api.enregistrerParametresSecurite(p).subscribe({
      next: (r) => {
        this.envoi.set(false);
        this.p.set(r);
        this.message.set({ ok: true, texte: 'Politique de sécurité enregistrée. Elle s’applique aux prochaines connexions.' });
      },
      error: (e) => {
        this.envoi.set(false);
        const err = lireErreur(e);
        this.message.set({ ok: false, texte: err.blocages.map((b) => b.message).join(' ') || err.message });
      },
    });
  }
}
