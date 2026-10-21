package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class TemporalHasher {
    private static final String FIELD_SEPARATOR = "#";
    private static final String FIELD_SEPARATOR2 = "|";

    public static String hashEntity(final Object entity) {
        return hashEntity(entity, currentVersion(entity.getClass()));
    }

    /** The hash the entity had before any identity field newer than {@code version} existed. */
    public static String hashEntity(final Object entity, final int version) {
        return hash(hashKey(entity, version));
    }

    /** Hashes under every earlier identity version, newest first, without the current hash. */
    public static List<String> olderHashes(final Object entity) {
        final String current = hashEntity(entity);
        return IntStream.iterate(currentVersion(entity.getClass()) - 1, v -> v >= 1, v -> v - 1)
                .mapToObj(v -> hashEntity(entity, v))
                .filter(hash -> !hash.equals(current))
                .distinct()
                .toList();
    }

    private static int currentVersion(final Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(PartOfNaturalKey.class))
                .mapToInt(f -> f.getAnnotation(PartOfNaturalKey.class).since())
                .max()
                .orElse(1);
    }

    private static String hashKey(final Object entity, final int version) {
        final StringBuilder sb = new StringBuilder();
        final Class<?> clazz = entity.getClass();
        if (clazz == Long.class || clazz == Boolean.class || clazz == Integer.class || clazz == String.class
                || clazz.isPrimitive()) {
            sb.append(entity).append(FIELD_SEPARATOR);
        } else {
            for (final Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                final PartOfNaturalKey key = field.getAnnotation(PartOfNaturalKey.class);
                if (key != null && key.since() <= version) {
                    try {
                        final Object obj = field.get(entity);
                        if (obj instanceof Collection<?>) {
                            sb.append(((Collection<?>)obj).stream()
                                    .map(e -> hashKey(e, Integer.MAX_VALUE))
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

    private static String hash(final String identityText) {
        try {
            final byte[] digest = MessageDigest.getInstance("MD5").digest(identityText.getBytes(Charset.defaultCharset()));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime supports MD5", e);
        }
    }

}
