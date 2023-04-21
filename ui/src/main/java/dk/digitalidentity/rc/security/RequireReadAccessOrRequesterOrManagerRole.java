package dk.digitalidentity.rc.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_READ_ACCESS') or hasRole('ROLE_MANAGER') or hasRole('ROLE_SUBSTITUTE') or hasRole('ROLE_REQUESTER')")
public @interface RequireReadAccessOrRequesterOrManagerRole {

}
