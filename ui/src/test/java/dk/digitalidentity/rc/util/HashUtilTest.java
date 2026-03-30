package dk.digitalidentity.rc.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class HashUtilTest {

	@Test
	@DisplayName("Samme input giver samme hash (deterministisk)")
	void testDeterministic() {
		String hash1 = HashUtil.generateHash("test", 123, "data");
		String hash2 = HashUtil.generateHash("test", 123, "data");

		assertThat(hash1).isEqualTo(hash2);
	}

	@Test
	@DisplayName("Forskelligt input giver forskellig hash")
	void testDifferentInputs() {
		String hash1 = HashUtil.generateHash("test", 123);
		String hash2 = HashUtil.generateHash("test", 124);

		assertThat(hash1).isNotEqualTo(hash2);
	}

	@Test
	@DisplayName("Hash længde er 43 tegn (Base64 encoded SHA-256)")
	void testHashLength() {
		String hash = HashUtil.generateHash("test");

		assertThat(hash).hasSize(43);
	}

	@Test
	@DisplayName("Short hash længde er 32 tegn")
	void testShortHashLength() {
		String hash = HashUtil.generateShortHash("test");

		assertThat(hash).hasSize(32);
	}

	@Test
	@DisplayName("Hash er URL-safe (ingen +, /, eller =)")
	void testUrlSafe() {
		String hash = HashUtil.generateHash("test", 123, "data with spaces");

		assertThat(hash)
			.doesNotContain("+")
			.doesNotContain("/")
			.doesNotContain("=");
	}

	@Test
	@DisplayName("Null værdi håndteres korrekt")
	void testNullValue() {
		String hash1 = HashUtil.generateHash("test", null, "data");
		String hash2 = HashUtil.generateHash("test", null, "data");

		assertThat(hash1)
			.isNotNull()
			.isEqualTo(hash2);
	}

	@Test
	@DisplayName("Null og tom string giver forskellige hashes")
	void testNullVsEmptyString() {
		String hashWithNull = HashUtil.generateHash("test", null, "data");
		String hashWithEmpty = HashUtil.generateHash("test", "", "data");

		assertThat(hashWithNull)
			.isNotEqualTo(hashWithEmpty)
			.as("Null og tom string skal give forskellige hashes");
	}

	@Test
	@DisplayName("Rækkefølge af input betyder noget")
	void testOrderMatters() {
		String hash1 = HashUtil.generateHash("a", "b", "c");
		String hash2 = HashUtil.generateHash("c", "b", "a");

		assertThat(hash1).isNotEqualTo(hash2);
	}

	@Test
	@DisplayName("Forskellige typer kan hashes sammen")
	void testMixedTypes() {
		String hash = HashUtil.generateHash(
			"string",
			123,
			456L,
			true,
			12.34
		);

		assertThat(hash)
			.isNotNull()
			.hasSize(43);
	}

	@Test
	@DisplayName("Tom input giver valid hash")
	void testEmptyInput() {
		String hash = HashUtil.generateHash();

		assertThat(hash)
			.isNotNull()
			.hasSize(43);
	}

	@Test
	@DisplayName("Builder giver samme resultat som direkte metode")
	void testBuilderEquivalence() {
		String directHash = HashUtil.generateHash("a", "b", "c");

		String builderHash = HashUtil.builder()
			.add("a")
			.add("b")
			.add("c")
			.build();

		assertThat(builderHash).isEqualTo(directHash);
	}

	@Test
	@DisplayName("Builder håndterer nullable korrekt")
	void testBuilderNullable() {
		TestObject obj = new TestObject("test");
		TestObject nullObj = null;

		String hash = HashUtil.builder()
			.add("field1")
			.addNullable(obj, TestObject::getName)
			.addNullable(nullObj, TestObject::getName)
			.build();

		assertThat(hash)
			.isNotNull()
			.hasSize(43);
	}

	@Test
	@DisplayName("Builder short hash er prefix af full hash")
	void testBuilderShortHashIsPrefix() {
		HashUtil.HashBuilder builder1 = HashUtil.builder().add("test").add(123);
		HashUtil.HashBuilder builder2 = HashUtil.builder().add("test").add(123);

		String fullHash = builder1.build();
		String shortHash = builder2.buildShort();

		assertThat(fullHash).startsWith(shortHash);
	}

	@Test
	@DisplayName("Stress test: 1000 forskellige hashes er unikke")
	void testUniqueness() {
		Set<String> hashes = new HashSet<>();

		for (int i = 0; i < 1000; i++) {
			String hash = HashUtil.generateHash("test", i, "data", System.nanoTime());
			hashes.add(hash);
		}

		assertThat(hashes)
			.hasSize(1000)
			.as("Alle hashes skal være unikke");
	}

	@Test
	@DisplayName("Performance test: 10000 hashes på under 1 sekund")
	void testPerformance() {
		long start = System.currentTimeMillis();

		for (int i = 0; i < 10000; i++) {
			HashUtil.generateHash("test", i, "data", "more", "fields");
		}

		long duration = System.currentTimeMillis() - start;

		assertThat(duration)
			.isLessThan(1000L)
			.as("10000 hashes skulle tage under 1 sekund, tog: %dms", duration);
	}

	@Test
	@DisplayName("Unicode karakterer håndteres korrekt")
	void testUnicodeCharacters() {
		String hash1 = HashUtil.generateHash("日本語", "한국어", "中文");
		String hash2 = HashUtil.generateHash("日本語", "한국어", "中文");

		assertThat(hash1)
			.isEqualTo(hash2)
			.hasSize(43);
	}

	@Test
	@DisplayName("Forskellige builder instances giver samme hash med samme data")
	void testDifferentBuildersSameData() {
		String hash1 = HashUtil.builder()
			.add("test")
			.add(123)
			.build();

		String hash2 = HashUtil.builder()
			.add("test")
			.add(123)
			.build();

		assertThat(hash1).isEqualTo(hash2);
	}

	@Test
	@DisplayName("AddNullable med null object giver samme resultat som add(null)")
	void testAddNullableEquivalence() {
		TestObject nullObj = null;

		String hash1 = HashUtil.builder()
			.add("field")
			.add(null)
			.build();

		String hash2 = HashUtil.builder()
			.add("field")
			.addNullable(nullObj, TestObject::getName)
			.build();

		assertThat(hash1).isEqualTo(hash2);
	}

	@Test
	@DisplayName("Short hash og full hash fra samme data har relation")
	void testShortAndFullHashRelation() {
		String shortHash = HashUtil.generateShortHash("test", 123);
		String fullHash = HashUtil.generateHash("test", 123);

		assertThat(fullHash)
			.startsWith(shortHash)
			.hasSize(43);

		assertThat(shortHash).hasSize(32);
	}

	// Helper klasse til test
	private static class TestObject {
		private final String name;

		public TestObject(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}
}
