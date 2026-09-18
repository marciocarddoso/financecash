package br.com.financecash.security;

import br.com.financecash.domain.model.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Adapta AppUser para o contrato do Spring Security. */
public class AuthenticatedUser implements UserDetails {

    private final AppUser appUser;

    public AuthenticatedUser(AppUser appUser) {
        this.appUser = appUser;
    }

    public UUID getId() {
        return appUser.getId();
    }

    public AppUser getAppUser() {
        return appUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return appUser.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return appUser.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return appUser.isActive();
    }
}
