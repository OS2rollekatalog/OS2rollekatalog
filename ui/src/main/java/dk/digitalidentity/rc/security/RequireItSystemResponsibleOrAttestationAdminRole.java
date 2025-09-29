package dk.digitalidentity.rc.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_IT_SYSTEM_RESPONSIBLE') || hasRole('ROLE_ADMINISTRATOR') || hasRole('ROLE_ATTESTATION_ADMINISTRATOR')")
public @interface RequireItSystemResponsibleOrAttestationAdminRole {

}
