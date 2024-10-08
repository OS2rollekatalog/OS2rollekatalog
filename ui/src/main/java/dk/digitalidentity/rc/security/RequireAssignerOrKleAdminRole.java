package dk.digitalidentity.rc.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_ASSIGNER') or hasRole('ROLE_KLE_ADMINISTRATOR')")
public @interface RequireAssignerOrKleAdminRole {

}
