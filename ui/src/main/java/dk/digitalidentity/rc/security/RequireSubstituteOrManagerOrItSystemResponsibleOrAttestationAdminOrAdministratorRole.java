package dk.digitalidentity.rc.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_ADMINISTRATOR') or hasRole('ROLE_ATTESTATION_ADMINISTRATOR') or hasRole('ROLE_MANAGER') or hasRole('ROLE_SUBSTITUTE') or hasRole('ROLE_IT_SYSTEM_RESPONSIBLE')")
public @interface RequireSubstituteOrManagerOrItSystemResponsibleOrAttestationAdminOrAdministratorRole {
    // This name must be some kind of record
}
