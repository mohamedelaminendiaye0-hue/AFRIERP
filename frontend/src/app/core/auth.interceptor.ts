import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './session.service';

/** Joint le jeton de session à chaque appel ; une session expirée renvoie à la connexion. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const jeton = session.jeton();
  const requete = jeton && req.url.startsWith('/api/') ? req.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : req;
  return next(requete).pipe(
    catchError((e: unknown) => {
      // Session expirée ou fermée : un seul renvoi vers la connexion, en gardant la page d'origine.
      if (e instanceof HttpErrorResponse && e.status === 401 && !req.url.startsWith('/api/auth/') && session.jeton() === jeton && jeton) {
        session.fermer();
        const url = router.url.split('?')[0];
        if (!url.startsWith('/connexion')) {
          router.navigate(['/connexion'], { queryParams: { retour: router.url, expiree: 1 } });
        }
      }
      if (e instanceof HttpErrorResponse && e.status === 403 && e.error?.blocages?.[0]?.code === 'MOT_DE_PASSE_A_CHANGER') {
        router.navigate(['/mot-de-passe']);
      }
      return throwError(() => e);
    }),
  );
};
