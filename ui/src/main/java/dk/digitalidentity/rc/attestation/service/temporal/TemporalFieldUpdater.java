package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import jakarta.persistence.Id;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;

public abstract class TemporalFieldUpdater {

    /**
     * Set target entity's property values to that of the source entity.
     * Properties marked with @PartOfNaturalKey or @Id will not be set on target.
     * Properties with non alpha names will not be set either.
     */
    public static void updateFields(final Object target, final Object source) {
        assert target.getClass() == source.getClass();
        final Class<?> clazz = source.getClass();
        try {
            for (final Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                if (!field.isAnnotationPresent(PartOfNaturalKey.class) &&
                        !field.isAnnotationPresent(Id.class) &&
                        StringUtils.isAlpha(field.getName())) { // Check the field is alpha (so we don't copy hibernates hidden state fields etc.)
                    field.set(target, field.get(source));
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
