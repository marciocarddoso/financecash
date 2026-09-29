import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

// Sem isso, os pipes `currency`/`date` caem no locale padrão do Angular (en-US) — formatando
// valor como "R$8,528.60" (vírgula de milhar, ponto decimal) em vez do padrão brasileiro
// "R$8.528,60" (28/09, oitava rodada, achado pelo Marcio comparando a tela com valores reais).
registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
  ],
};
