package br.com.financecash.security;

import br.com.financecash.domain.model.AppUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolve o AppUser autenticado a partir do SecurityContext, para os services usarem no escopo das queries. */
@Component
public class CurrentUserProvider {

    public AppUser getCurrentUser() {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return principal.getAppUser();
    }
}
