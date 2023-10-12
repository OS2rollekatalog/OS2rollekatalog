package dk.digitalidentity.rc.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_MANAGER') or hasRole('ROLE_SUBSTITUTE')")
public @interface RequireSubstituteOrManagerRole {

}
