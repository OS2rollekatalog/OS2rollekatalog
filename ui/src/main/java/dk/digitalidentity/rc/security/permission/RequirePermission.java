package dk.digitalidentity.rc.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods in controllers, that ensures only users with the correct Permission
 * for a specific Entity is allowed to access that method.
 * If the controller is also annotated with RequireControllerPermission, then
 * users have to satisfy BOTH annotations to gain access
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
	Section section();
	Permission permission();
}
