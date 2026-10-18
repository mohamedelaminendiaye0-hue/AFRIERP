import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionService } from './session.service';

/** Transmet l'utilisateur qui agit a l'API (en-tete X-Utilisateur). */
export const utilisateurInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionService);
  return next(req.clone({ setHeaders: { 'X-Utilisateur': session.id() } }));
};
