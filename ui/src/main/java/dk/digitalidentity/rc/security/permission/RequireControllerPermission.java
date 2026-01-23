package dk.digitalidentity.rc.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for Controllers that ensures only users with the correct Permission
 * for a specific Entity is allowed to access that controller.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireControllerPermission {
	Section section();
	Permission permission();
}
