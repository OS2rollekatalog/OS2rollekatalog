package dk.digitalidentity.rc.attestation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Changes to fields marked with the annotation will cause a new entity to be generated.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PartOfNaturalKey {
}
