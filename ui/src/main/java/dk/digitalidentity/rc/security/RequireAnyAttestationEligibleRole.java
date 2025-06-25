package dk.digitalidentity.rc.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_ADMINISTRATOR') or hasRole('ROLE_ATTESTATION_ADMINISTRATOR') or hasRole('ROLE_MANAGER') or hasRole('ROLE_SUBSTITUTE') or hasRole('ROLE_IT_SYSTEM_RESPONSIBLE') or hasRole('ROLE_MANAGER_SUBSTITUDE')")
public @interface RequireAnyAttestationEligibleRole {
    // This name must be some kind of record
}
