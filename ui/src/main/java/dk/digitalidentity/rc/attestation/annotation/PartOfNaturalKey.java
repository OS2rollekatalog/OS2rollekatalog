package dk.digitalidentity.rc.attestation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Changes to fields marked with the annotation will cause a new section to be generated.
 * Adding a field to an existing entity's identity: declare it last and give it the next {@link #since()}
 * version.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PartOfNaturalKey {

	/** The identity version that introduced this field. */
	int since() default 1;
}
