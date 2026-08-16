package com.shop.online_shop.security;

import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Stream;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String fullName;
    private final String roleName;
    private final UserStatus status;
    private final boolean mustChangePassword;
    private final Collection<? extends GrantedAuthority> authorities;

    private UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.fullName = user.getFullName();
        this.roleName = user.getRole().getName();
        this.status = user.getStatus();
        this.mustChangePassword = user.isMustChangePassword();

        this.authorities = Stream.concat(
                user.getRole().getPermissions().stream().map(Permission::getName),
                Stream.of("ROLE_" + user.getRole().getName())
        ).map(SimpleGrantedAuthority::new).toList();
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user);
    }

    public boolean hasAuthority(String authority) {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return status != UserStatus.SUSPENDED; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return status.canLogin(); }
}