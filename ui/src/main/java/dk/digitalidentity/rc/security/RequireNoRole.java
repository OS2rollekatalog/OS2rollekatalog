package dk.digitalidentity.rc.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@RequireRoleAnnotation
public @interface RequireNoRole {

}
