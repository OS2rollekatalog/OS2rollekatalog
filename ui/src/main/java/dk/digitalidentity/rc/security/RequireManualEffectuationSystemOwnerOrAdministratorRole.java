package dk.digitalidentity.rc.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_MANUAL_EFFECTUATION_SYSTEM_OWNER') || hasRole('ROLE_ADMINISTRATOR')")
@RequireRoleAnnotation
public @interface RequireManualEffectuationSystemOwnerOrAdministratorRole {

}
