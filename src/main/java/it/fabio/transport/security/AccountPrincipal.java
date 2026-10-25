package it.fabio.transport.security;

import java.util.Collection;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AccountPrincipal(UserAccount account) implements UserDetails {
    public Collection<? extends GrantedAuthority> getAuthorities() { return java.util.List.of(new SimpleGrantedAuthority("ROLE_"+account.user().role())); }
    public String getPassword() { return account.passwordHash(); }
    public String getUsername() { return account.user().username(); }
    public boolean isEnabled() { return account.enabled(); }
    public boolean isAccountNonExpired() { return true; }
    public boolean isAccountNonLocked() { return true; }
    public boolean isCredentialsNonExpired() { return true; }
}
