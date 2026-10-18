import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { accueilDe } from '../core/gardes';
import { IdentiteService } from '../core/identite.service';
import { CompteDemo } from '../core/models';
import { SessionService } from '../core/session.service';
import { VERSION } from '../core/version';

@Component({
  selector: 'app-connexion',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './connexion.component.html',
  styleUrl: './connexion.component.scss',
})
export class ConnexionComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly identite = inject(IdentiteService).identite;

  protected email = '';
  protected motDePasse = '';
  protected readonly visible = signal(false);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly comptes = signal<CompteDemo[]>([]);
  protected readonly expiree = signal(false);
  protected readonly version = VERSION;

  ngOnInit(): void {
    if (this.session.connecte()) {
      this.router.navigateByUrl(this.session.doitChangerMotDePasse() ? '/mot-de-passe' : accueilDe(this.session.utilisateur()?.role));
      return;
    }
    this.expiree.set(this.route.snapshot.queryParamMap.has('expiree'));
    this.api.comptesDemo().subscribe({ next: (c) => this.comptes.set(c), error: () => this.comptes.set([]) });
  }

  protected choisir(c: CompteDemo): void {
    this.email = c.email;
    this.motDePasse = c.motDePasse;
    this.connecter();
  }

  protected connecter(): void {
    if (!this.email.trim() || !this.motDePasse) {
      this.erreur.set('Saisissez votre adresse e-mail et votre mot de passe.');
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.api.connexion(this.email.trim(), this.motDePasse).subscribe({
      next: (s) => {
        this.session.ouvrir(s);
        if (s.doitChangerMotDePasse) {
          this.router.navigate(['/mot-de-passe']);
          return;
        }
        const retour = this.route.snapshot.queryParamMap.get('retour');
        this.router.navigateByUrl(retour && retour !== '/' && !retour.startsWith('/connexion') ? retour : accueilDe(s.utilisateur.role));
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(e?.status === 401 ? 'Adresse e-mail ou mot de passe incorrect.' : "Le serveur ne répond pas. Réessayez dans un instant.");
      },
    });
  }

  protected teinte(role: string): string {
    if (role.startsWith('NOTAIRE')) return 'var(--accent)';
    if (role === 'PRINCIPAL_CLERC' || role === 'CLERC') return 'var(--primaire)';
    if (role === 'COMPTABLE_TAXATEUR' || role === 'FORMALISTE') return 'var(--bon)';
    if (role === 'SUPER_ADMIN') return 'var(--critique)';
    return '#6b5b95';
  }
}
