package dk.digitalidentity.rc.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_API_AUDITLOG_ACCESS')")
public @interface RequireApiAuditlogAccessRole {

}
