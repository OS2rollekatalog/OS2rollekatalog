package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import lombok.extern.slf4j.Slf4j;

import javax.xml.bind.DatatypeConverter;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.stream.Collectors;

@Slf4j
public abstract class TemporalHasher {
    private static final MessageDigest md;
    private static final String FIELD_SEPARATOR = "#";
    private static final String FIELD_SEPARATOR2 = "|";
    static {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashEntity(final Object entity) {
        final String hashKey = hashKey(entity);
        md.update(hashKey.getBytes());
        return DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
    }

    private static String hashKey(final Object entity) {
        final StringBuilder sb = new StringBuilder();
        final Class<?> clazz = entity.getClass();
        if (clazz == Long.class || clazz == Boolean.class || clazz == Integer.class || clazz == String.class
                || clazz.isPrimitive()) {
            sb.append(entity).append(FIELD_SEPARATOR);
        } else {
            for (final Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.isAnnotationPresent(PartOfNaturalKey.class)) {
                    try {
                        final Object obj = field.get(entity);
                        if (obj instanceof Collection<?>) {
                            sb.append(((Collection<?>)obj).stream()
                                    .map(TemporalHasher::hashKey)
                                    .sorted() // Sort so sets won't mess it up
                                    .collect(Collectors.joining(FIELD_SEPARATOR2)));
                        } else {
                            sb.append(obj);
                        }
                        sb.append(FIELD_SEPARATOR);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return sb.toString();
    }

}
