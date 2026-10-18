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
      if (e instanceof HttpErrorResponse && e.status === 401 && !req.url.startsWith('/api/auth/')) {
        session.fermer();
        router.navigate(['/connexion'], { queryParams: { retour: router.url, expiree: 1 } });
      }
      return throwError(() => e);
    }),
  );
};
