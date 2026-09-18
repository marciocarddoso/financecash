import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Anexa o token JWT (salvo pelo AuthService) em toda chamada para a API.
 * Autenticação em si (tela de login) fica no roadmap da UI — hoje o token
 * pode ser obtido via POST /api/auth/login e configurado manualmente,
 * este interceptor já deixa o transporte pronto.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('financecash_token');

  if (!token) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
