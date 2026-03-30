package dk.digitalidentity.rc.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.function.Function;

/**
 * Utility klasse til at generere deterministiske hashes af objekter.
 * Bruger SHA-256 for at sikre ingen kollisioner selv med millioner af records.
 *
 * <p>Eksempel brug:</p>
 * <pre>
 * // Simpel metode
 * String hash = HashUtil.generateHash(id, name, timestamp);
 *
 * // Builder pattern (anbefalet for komplekse objekter)
 * String hash = HashUtil.builder()
 *     .add(user.getId())
 *     .add(user.getName())
 *     .addNullable(user.getManager(), Manager::getId)
 *     .build();
 * </pre>
 */
public class HashUtil {

	private static final String DELIMITER = "\u001F"; // ASCII Unit Separator - kan ikke forekomme i normale strings
	private static final String NULL_PLACEHOLDER = "\u0000NULL\u0000"; // Kan ikke forekomme i normale strings
	private static final String ALGORITHM = "SHA-256";

	private HashUtil() {
		// Utility class - prevent instantiation
	}

	/**
	 * Genererer en SHA-256 hash baseret på de givne felter.
	 *
	 * @param fields Værdierne der skal hashes (i rækkefølge)
	 * @return Base64 URL-safe hash string (43 tegn)
	 * @throws RuntimeException hvis SHA-256 algoritmen ikke er tilgængelig
	 */
	public static String generateHash(Object... fields) {
		try {
			String content = buildContent(fields);
			byte[] hashBytes = computeHash(content);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 algorithm not available", e);
		}
	}

	/**
	 * Genererer en kortere hash (32 tegn, ~192 bits sikkerhed).
	 * Stadig mere end nok sikkerhed til millioner af records.
	 *
	 * @param fields Værdierne der skal hashes (i rækkefølge)
	 * @return Forkortet Base64 URL-safe hash string (32 tegn)
	 */
	public static String generateShortHash(Object... fields) {
		return generateHash(fields).substring(0, 32);
	}

	/**
	 * Builder pattern for mere læsbar og type-safe kode.
	 * Anbefales når der skal hashes mange felter eller komplekse objekter.
	 */
	public static class HashBuilder {
		private final StringBuilder content = new StringBuilder();
		private boolean first = true;

		/**
		 * Tilføjer en værdi til hashen.
		 *
		 * @param value Værdien der skal tilføjes
		 * @return this builder
		 */
		public HashBuilder add(Object value) {
			if (!first) {
				content.append(DELIMITER);
			}
			content.append(fieldToString(value));
			first = false;
			return this;
		}

		/**
		 * Tilføjer en nullable værdi ved at ekstrahere en property.
		 * Nyttigt for at undgå NullPointerException på nested objekter.
		 *
		 * @param value Det nullable objekt
		 * @param extractor Funktion til at ekstrahere værdien
		 * @return this builder
		 */
		public <T> HashBuilder addNullable(T value, Function<T, Object> extractor) {
			return add(value != null ? extractor.apply(value) : NULL_PLACEHOLDER);
		}

		/**
		 * Bygger den fulde hash (43 tegn).
		 *
		 * @return Base64 URL-safe hash string
		 * @throws RuntimeException hvis SHA-256 algoritmen ikke er tilgængelig
		 */
		public String build() {
			try {
				byte[] hashBytes = computeHash(content.toString());
				return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("SHA-256 algorithm not available", e);
			}
		}

		/**
		 * Bygger en forkortet hash (32 tegn).
		 *
		 * @return Forkortet Base64 URL-safe hash string
		 */
		public String buildShort() {
			return build().substring(0, 32);
		}
	}

	/**
	 * Factory method til at oprette en ny HashBuilder.
	 *
	 * @return en ny HashBuilder instance
	 */
	public static HashBuilder builder() {
		return new HashBuilder();
	}

	/**
	 * Bygger content string fra fields array.
	 */
	private static String buildContent(Object[] fields) {
		StringBuilder content = new StringBuilder();
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				content.append(DELIMITER);
			}
			content.append(fieldToString(fields[i]));
		}
		return content.toString();
	}

	/**
	 * Beregner SHA-256 hash af content string.
	 */
	private static byte[] computeHash(String content) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
		return digest.digest(content.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Konverterer et felt til string representation.
	 * Null værdier konverteres til speciel null placeholder.
	 */
	private static String fieldToString(Object field) {
		if (field == null) {
			return NULL_PLACEHOLDER;
		}
		return field.toString();
	}
}
