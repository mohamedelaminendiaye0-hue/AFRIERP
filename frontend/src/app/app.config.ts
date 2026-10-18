import { APP_INITIALIZER, ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { IdentiteService } from './core/identite.service';
import { TitreStrategy } from './core/titre.strategy';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    { provide: TitleStrategy, useClass: TitreStrategy },
    // La marque blanche est appliquée avant le premier affichage (pas de flash de couleurs).
    { provide: APP_INITIALIZER, multi: true, deps: [IdentiteService], useFactory: (i: IdentiteService) => () => i.charger() },
  ],
};
