import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from './models';
import { SessionService } from './session.service';

/** Espace de travail réservé aux utilisateurs connectés. */
export const connecteGarde: CanActivateFn = (_route, etat) => {
  const session = inject(SessionService);
  if (session.connecte()) return true;
  session.fermer();
  return inject(Router).createUrlTree(['/connexion'], { queryParams: { retour: etat.url } });
};

/** Écran réservé à certains profils ; les autres retrouvent leur accueil. */
export function roleGarde(...roles: Role[]): CanActivateFn {
  return () => {
    const session = inject(SessionService);
    return session.a(...roles) || inject(Router).createUrlTree([accueilDe(session.utilisateur()?.role)]);
  };
}

/** Les membres de l'étude n'accèdent pas à l'administration, et inversement. */
export const membreEtudeGarde: CanActivateFn = () => {
  const session = inject(SessionService);
  return !session.a('SUPER_ADMIN') || inject(Router).createUrlTree(['/administration/identite']);
};

export function accueilDe(role: Role | undefined): string {
  return role === 'SUPER_ADMIN' ? '/administration/identite' : '/tableau-de-bord';
}
