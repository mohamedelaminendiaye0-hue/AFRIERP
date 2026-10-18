import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService, lireErreur } from '../core/api.service';
import { accueilDe } from '../core/gardes';
import { IdentiteService } from '../core/identite.service';
import { SessionService } from '../core/session.service';

/**
 * Changement de mot de passe. Imposé à la première connexion (mot de passe
 * provisoire remis par l'administrateur) ; accessible ensuite depuis le profil.
 */
@Component({
  selector: 'app-mot-de-passe',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="ecran">
      <main class="boite carte">
        <div class="tete">
          @if (identite().logo; as logo) { <img [src]="logo" alt="" class="logo" /> }
          @else { <span class="sceau" aria-hidden="true">{{ identite().monogramme || identite().nom.charAt(0) }}</span> }
          <span class="etude">{{ identite().nom }}</span>
        </div>
        <p class="surtitre">{{ force() ? 'Première connexion' : 'Sécurité du compte' }}</p>
        <h1>{{ force() ? 'Choisissez votre mot de passe' : 'Changer mon mot de passe' }}</h1>
        <p class="muet intro">
          @if (force()) {
            Bonjour {{ session.utilisateur()?.prenom }}, votre compte a été ouvert avec un mot de passe provisoire.
            Remplacez-le par un mot de passe personnel pour accéder à l'espace de travail.
          } @else {
            Vos autres sessions ouvertes seront fermées.
          }
        </p>
        <form (ngSubmit)="valider()" novalidate>
          <div class="champ">
            <label for="actuel">{{ force() ? 'Mot de passe provisoire' : 'Mot de passe actuel' }}</label>
            <input id="actuel" name="actuel" type="password" autocomplete="current-password" [ngModel]="actuel()" (ngModelChange)="actuel.set($event)" />
          </div>
          <div class="champ">
            <label for="nouveau">Nouveau mot de passe</label>
            <input id="nouveau" name="nouveau" [type]="visible() ? 'text' : 'password'" autocomplete="new-password"
                   [ngModel]="nouveau()" (ngModelChange)="nouveau.set($event)" />
            <ul class="regles" aria-live="polite">
              <li [class.ok]="nouveau().length >= longueur()">Au moins {{ longueur() }} caractères</li>
              <li [class.ok]="lettresEtChiffres()">Des lettres et des chiffres</li>
            </ul>
          </div>
          <div class="champ">
            <label for="confirmation">Confirmer le nouveau mot de passe</label>
            <input id="confirmation" name="confirmation" [type]="visible() ? 'text' : 'password'" autocomplete="new-password"
                   [ngModel]="confirmation()" (ngModelChange)="confirmation.set($event)" />
            @if (confirmation() && confirmation() !== nouveau()) { <p class="erreur">Les deux saisies ne correspondent pas.</p> }
          </div>
          <label class="voir"><input type="checkbox" [checked]="visible()" (change)="visible.set(!visible())" /> Afficher les mots de passe</label>
          @if (erreur(); as e) { <div class="bandeau bandeau--critique" role="alert">{{ e }}</div> }
          <div class="actions">
            @if (force()) {
              <button class="bouton bouton--fantome" type="button" (click)="deconnexion()">Se déconnecter</button>
            } @else {
              <button class="bouton bouton--fantome" type="button" (click)="retour()">Annuler</button>
            }
            <button class="bouton" type="submit" [disabled]="!pret() || envoi()">{{ envoi() ? 'Enregistrement…' : 'Enregistrer le mot de passe' }}</button>
          </div>
        </form>
      </main>
    </div>
  `,
  styles: [`
    .ecran { min-height: 100vh; display: grid; place-items: center; padding: 24px 16px; background: var(--fond); }
    .boite { width: min(480px, 100%); padding: 28px; }
    .tete { display: flex; align-items: center; gap: 12px; margin-bottom: 22px; }
    .sceau { width: 40px; height: 40px; border-radius: 50%; border: 1.5px solid var(--accent); color: var(--accent-texte);
      display: grid; place-items: center; font-weight: 700; }
    .logo { width: 40px; height: 40px; object-fit: contain; }
    .etude { font-weight: 700; }
    h1 { font-size: 26px; }
    .intro { margin: 8px 0 22px; }
    .regles { list-style: none; margin: 8px 0 0; padding: 0; display: flex; gap: 14px; flex-wrap: wrap; font-size: 13.5px; color: var(--texte-2); }
    .regles li::before { content: '○ '; }
    .regles li.ok { color: var(--bon); font-weight: 700; }
    .regles li.ok::before { content: '✓ '; }
    .erreur { color: var(--critique); font-weight: 700; margin: 6px 0 0; font-size: 14px; }
    .voir { display: flex; align-items: center; gap: 8px; font-weight: 400; color: var(--texte-2); margin: 4px 0 16px; cursor: pointer; }
    .bandeau { margin-bottom: 14px; }
    .actions { display: flex; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
  `],
})
export class MotDePasseComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  protected readonly session = inject(SessionService);
  protected readonly identite = inject(IdentiteService).identite;

  protected readonly actuel = signal('');
  protected readonly nouveau = signal('');
  protected readonly confirmation = signal('');
  protected readonly visible = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly longueur = signal(10);

  protected readonly force = computed(() => this.session.doitChangerMotDePasse());
  protected readonly lettresEtChiffres = computed(() => /\p{L}/u.test(this.nouveau()) && /\d/.test(this.nouveau()));
  protected readonly pret = computed(() =>
    !!this.actuel() && this.nouveau().length >= this.longueur() && this.lettresEtChiffres() && this.nouveau() === this.confirmation(),
  );

  ngOnInit(): void {
    this.api.politiqueMotDePasse().subscribe((p) => this.longueur.set(p.longueurMinimale));
  }

  protected valider(): void {
    if (!this.pret()) return;
    this.envoi.set(true);
    this.erreur.set(null);
    this.api.changerMotDePasse(this.actuel(), this.nouveau()).subscribe({
      next: (s) => {
        this.session.ouvrir(s);
        this.router.navigateByUrl(accueilDe(s.utilisateur.role));
      },
      error: (e) => {
        this.envoi.set(false);
        const err = lireErreur(e);
        this.erreur.set(err.blocages.map((b) => b.message).join(' ') || err.message);
      },
    });
  }

  protected retour(): void {
    history.length > 1 ? history.back() : this.router.navigateByUrl(accueilDe(this.session.utilisateur()?.role));
  }

  protected deconnexion(): void {
    this.session.fermer();
    this.router.navigate(['/connexion']);
  }
}
