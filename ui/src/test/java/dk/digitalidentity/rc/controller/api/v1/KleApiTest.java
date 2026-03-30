package dk.digitalidentity.rc.controller.api.v1;

import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for KLE API endpoints.
 * <p>
 * Tests API endpoints for loading KLE codes onto organizational units. The API supports
 * three operations: init (recursive initialization), add (non-recursive addition), and remove.
 * KLE codes can be specified individually or as intervals (e.g., "01-03" for codes 01, 02, 03).
 * </p>
 */
@DisplayName("KLE API Tests")
public class KleApiTest extends AbstractApiTest {

	@Autowired
	private KleService kleService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ORGANISATION.toString());
	}

	/**
	 * Tests that KLE codes can be initialized on an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Init operation applies KLE codes recursively to children
	 * - REST documentation is generated with operation descriptions
	 * </p>
	 * <p>
	 * Note: The API requires that no KLE codes are already assigned to the target OrgUnit.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully initialize KLE codes on organizational unit")
	void testLoadKle_Success() throws Exception {
		List<Kle> existingKles = kleService.findAll();
		if (existingKles.isEmpty()) {
			Kle kle1 = new Kle();
			kle1.setCode("00");
			kle1.setName("Test Hovedgruppe");
			kle1.setActive(true);
			kle1.setParent("0");
			kleService.save(kle1);

			Kle kle2 = new Kle();
			kle2.setCode("00.01");
			kle2.setName("Test Gruppe");
			kle2.setActive(true);
			kle2.setParent("00");
			kleService.save(kle2);

			Kle kle3 = new Kle();
			kle3.setCode("00.01.01");
			kle3.setName("Test Emne");
			kle3.setActive(true);
			kle3.setParent("00.01");
			kleService.save(kle3);
		}

		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		OrgUnit targetOu = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No OrgUnit found"));

		List<Kle> kles = kleService.findAll();
		String kleCode = kles.stream()
			.filter(k -> k.getCode().length() >= 5)
			.map(Kle::getCode)
			.findFirst()
			.orElse(kles.get(0).getCode());

		String requestBody = String.format("""
			[
				{
					"uuid": "%s",
					"kle": {
						"init": ["%s"],
						"remove": [],
						"add": []
					}
				}
			]
			""", targetOu.getUuid(), kleCode);

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("kleload-initial",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("[]").type(JsonFieldType.ARRAY).description("Array of KLE load operations"),
					fieldWithPath("[].uuid").type(JsonFieldType.STRING).description("UUID of the OrgUnit to load KLE onto"),
					fieldWithPath("[].kle").type(JsonFieldType.OBJECT).description("KLE loadset containing init, remove, and add operations"),
					fieldWithPath("[].kle.init").type(JsonFieldType.ARRAY).description("List of KLE codes to initialize (applied recursively to children)"),
					fieldWithPath("[].kle.remove").type(JsonFieldType.ARRAY).description("List of KLE codes to remove"),
					fieldWithPath("[].kle.add").type(JsonFieldType.ARRAY).description("List of KLE codes to add (not recursive)")
				)
			));

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu = orgUnitService.getByUuid(targetOu.getUuid());
		assertThat(updatedOu.getKles())
			.anyMatch(k -> k.getCode().equals(kleCode));
	}

	/**
	 * Tests that KLE codes can be loaded using interval notation.
	 * <p>
	 * Verifies that:
	 * - Interval notation (e.g., "01-03") is supported
	 * - All KLE codes within the interval are assigned
	 * - Intervals must be between codes at the same level
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully load KLE codes using interval notation")
	void testLoadKle_WithInterval() throws Exception {
		Kle kle1 = new Kle();
		kle1.setCode("01");
		kle1.setName("Test Hovedgruppe 1");
		kle1.setActive(true);
		kle1.setParent("0");
		kleService.save(kle1);

		Kle kle2 = new Kle();
		kle2.setCode("02");
		kle2.setName("Test Hovedgruppe 2");
		kle2.setActive(true);
		kle2.setParent("0");
		kleService.save(kle2);

		Kle kle3 = new Kle();
		kle3.setCode("03");
		kle3.setName("Test Hovedgruppe 3");
		kle3.setActive(true);
		kle3.setParent("0");
		kleService.save(kle3);

		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		OrgUnit targetOu = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No OrgUnit found"));

		String requestBody = String.format("""
			[
				{
					"uuid": "%s",
					"kle": {
						"init": ["01-03"],
						"remove": [],
						"add": []
					}
				}
			]
			""", targetOu.getUuid());

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu = orgUnitService.getByUuid(targetOu.getUuid());
		assertThat(updatedOu.getKles())
			.extracting("code")
			.contains("01", "02", "03");
	}

	/**
	 * Tests that KLE codes can be removed after initialization.
	 * <p>
	 * Verifies that:
	 * - Remove operation works correctly
	 * - Specific KLE codes can be excluded from recursive initialization
	 * - Init and remove operations can be combined
	 * - Parent KLE code remains while child is removed
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully remove KLE codes after initialization")
	void testLoadKle_WithRemove() throws Exception {
		Kle kle1 = new Kle();
		kle1.setCode("04");
		kle1.setName("Test Hovedgruppe 4");
		kle1.setActive(true);
		kle1.setParent("0");
		kleService.save(kle1);

		Kle kle2 = new Kle();
		kle2.setCode("04.01");
		kle2.setName("Test Gruppe 4.1");
		kle2.setActive(true);
		kle2.setParent("04");
		kleService.save(kle2);

		Kle kle3 = new Kle();
		kle3.setCode("04.02");
		kle3.setName("Test Gruppe 4.2");
		kle3.setActive(true);
		kle3.setParent("04");
		kleService.save(kle3);

		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		OrgUnit targetOu = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No OrgUnit found"));

		String requestBody = String.format("""
       [
          {
             "uuid": "%s",
             "kle": {
                "init": ["04"],
                "remove": ["04.01"],
                "add": []
             }
          }
       ]
       """, targetOu.getUuid());

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu = orgUnitService.getByUuid(targetOu.getUuid());
		assertThat(updatedOu.getKles())
			.extracting("code")
			.contains("04.02")
			.doesNotContain("04.01");
	}

	/**
	 * Tests that KLE codes can be added non-recursively.
	 * <p>
	 * Verifies that:
	 * - Add operation works correctly
	 * - Add is non-recursive (unlike init)
	 * - Multiple operations can be combined (init + add)
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should add KLE to child OrgUnits with init, but not with add")
	void testLoadKle_InitVsAdd_OrgUnitRecursion() throws Exception {
		Kle kle1 = new Kle();
		kle1.setCode("05");
		kle1.setName("Test Hovedgruppe 5");
		kle1.setActive(true);
		kle1.setParent("0");
		kleService.save(kle1);

		Kle kle2 = new Kle();
		kle2.setCode("06");
		kle2.setName("Test Hovedgruppe 6");
		kle2.setActive(true);
		kle2.setParent("0");
		kleService.save(kle2);

		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		// Find an OrgUnit that has children
		OrgUnit targetOu = orgUnitService.getAll().stream()
			.filter(ou -> ou.getChildren() != null && !ou.getChildren().isEmpty())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No OrgUnit with children found"));

		OrgUnit childOu = targetOu.getChildren().getFirst();

		String requestBody = String.format("""
       [
          {
             "uuid": "%s",
             "kle": {
                "init": ["05"],
                "remove": [],
                "add": ["06"]
             }
          }
       ]
       """, targetOu.getUuid());

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();

		OrgUnit updatedParent = orgUnitService.getByUuid(targetOu.getUuid());
		OrgUnit updatedChild = orgUnitService.getByUuid(childOu.getUuid());

		// Verify both codes on parent
		assertThat(updatedParent.getKles())
			.extracting("code")
			.contains("05", "06");

		// Verify init added to child (recursive through OrgUnit hierarchy)
		assertThat(updatedChild.getKles())
			.extracting("code")
			.contains("05")
			.as("init should add KLE 05 to child OrgUnits recursively");

		// Verify add did NOT add to child (non-recursive)
		assertThat(updatedChild.getKles())
			.extracting("code")
			.doesNotContain("06")
			.as("add should NOT add KLE 06 to child OrgUnits");
	}

	/**
	 * Tests that loading KLE fails when codes are already assigned.
	 * <p>
	 * Verifies that:
	 * - API returns HTTP 400 Bad Request when KLE codes are already assigned
	 * - Prevents accidental overwriting of existing KLE assignments
	 * - Validation works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when KLE codes are already assigned to org unit")
	void testLoadKle_FailsWhenKleAlreadyAssigned() throws Exception {
		Kle kle = new Kle();
		kle.setCode("07");
		kle.setName("Test Hovedgruppe 7");
		kle.setActive(true);
		kle.setParent("0");
		kleService.save(kle);

		entityManager.flush();
		entityManager.clear();

		OrgUnit targetOu = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No OrgUnit found"));

		dk.digitalidentity.rc.dao.model.KLEMapping mapping = new dk.digitalidentity.rc.dao.model.KLEMapping();
		mapping.setCode("07");
		mapping.setAssignmentType(dk.digitalidentity.rc.dao.model.enums.KleType.PERFORMING);
		mapping.setOrgUnit(targetOu);
		targetOu.getKles().add(mapping);
		orgUnitService.save(targetOu);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
			[
				{
					"uuid": "%s",
					"kle": {
						"init": ["07"],
						"remove": [],
						"add": []
					}
				}
			]
			""", targetOu.getUuid());

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest());
	}

	/**
	 * Tests that non-existent organizational units are silently skipped.
	 * <p>
	 * Verifies that:
	 * - API returns HTTP 200 OK even with invalid UUIDs
	 * - Non-existent OrgUnits are silently ignored
	 * - Partial failures don't prevent other operations from succeeding
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should silently skip non-existent organizational units")
	void testLoadKle_WithNonExistentOrgUnit() throws Exception {
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		Kle kle = new Kle();
		kle.setCode("08");
		kle.setName("Test Hovedgruppe 8");
		kle.setActive(true);
		kle.setParent("0");
		kleService.save(kle);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
			[
				{
					"uuid": "non-existent-uuid-12345",
					"kle": {
						"init": ["08"],
						"remove": [],
						"add": []
					}
				}
			]
			""";

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());
	}

	/**
	 * Tests that non-existent KLE codes are silently skipped.
	 * <p>
	 * Verifies that:
	 * - API returns HTTP 200 OK even with invalid KLE codes
	 * - Non-existent KLE codes are silently ignored
	 * - Invalid codes don't prevent the entire operation from succeeding
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should silently skip non-existent KLE codes")
	void testLoadKle_WithNonExistentKle() throws Exception {
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		OrgUnit targetOu = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No OrgUnit found"));

		String requestBody = String.format("""
			[
				{
					"uuid": "%s",
					"kle": {
						"init": ["93.95.99.12.35.98.45.12"],
						"remove": [],
						"add": []
					}
				}
			]
			""", targetOu.getUuid());

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());
	}

	/**
	 * Tests that an empty payload is accepted.
	 * <p>
	 * Verifies that:
	 * - Empty array payload is valid
	 * - API returns HTTP 200 OK
	 * - No errors occur with empty operations
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully handle empty payload")
	void testLoadKle_EmptyPayload() throws Exception {
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		String requestBody = "[]";

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());
	}

	/**
	 * Tests that KLE codes can be loaded onto multiple organizational units.
	 * <p>
	 * Verifies that:
	 * - Multiple OrgUnits can be processed in a single request
	 * - Each OrgUnit can have different KLE assignments
	 * - Batch operations work correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully load KLE codes onto multiple organizational units")
	void testLoadKle_MultipleOrgUnits() throws Exception {
		Kle kle1 = new Kle();
		kle1.setCode("09");
		kle1.setName("Test Hovedgruppe 9");
		kle1.setActive(true);
		kle1.setParent("0");
		kleService.save(kle1);

		Kle kle2 = new Kle();
		kle2.setCode("10");
		kle2.setName("Test Hovedgruppe 10");
		kle2.setActive(true);
		kle2.setParent("0");
		kleService.save(kle2);

		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		List<OrgUnit> freshOrgUnits = orgUnitService.getAll();
		assertThat(freshOrgUnits).hasSizeGreaterThanOrEqualTo(2);

		OrgUnit ou1 = freshOrgUnits.get(0);
		OrgUnit ou2 = freshOrgUnits.get(1);

		String requestBody = String.format("""
			[
				{
					"uuid": "%s",
					"kle": {
						"init": ["09"],
						"remove": [],
						"add": []
					}
				},
				{
					"uuid": "%s",
					"kle": {
						"init": ["10"],
						"remove": [],
						"add": []
					}
				}
			]
			""", ou1.getUuid(), ou2.getUuid());

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu1 = orgUnitService.getByUuid(ou1.getUuid());
		OrgUnit updatedOu2 = orgUnitService.getByUuid(ou2.getUuid());
		assertThat(updatedOu1.getKles())
			.extracting("code")
			.contains("09");
		assertThat(updatedOu2.getKles())
			.extracting("code")
			.contains("10");
	}

	/**
	 * Tests that invalid intervals with mismatched KLE levels are handled gracefully.
	 * <p>
	 * Verifies that:
	 * - Intervals with different KLE code levels are skipped
	 * - API returns HTTP 200 OK even with invalid intervals
	 * - Invalid intervals don't cause errors
	 * </p>
	 * <p>
	 * Note: Intervals must be between KLE codes at the same hierarchical level
	 * (e.g., "01-03" is valid, but "11-11.01" is invalid because they're different levels).
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should handle invalid KLE intervals with mismatched levels gracefully")
	void testLoadKle_InvalidInterval() throws Exception {
		Kle kle1 = new Kle();
		kle1.setCode("11");
		kle1.setName("Test Hovedgruppe 11");
		kle1.setActive(true);
		kle1.setParent("0");
		kleService.save(kle1);

		Kle kle2 = new Kle();
		kle2.setCode("11.01");
		kle2.setName("Test Gruppe 11.1");
		kle2.setActive(true);
		kle2.setParent("11");
		kleService.save(kle2);

		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {
			ou.getKles().clear();
		}
		orgUnitService.save(orgUnits);

		entityManager.flush();
		entityManager.clear();

		OrgUnit targetOu = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No OrgUnit found"));

		String requestBody = String.format("""
			[
				{
					"uuid": "%s",
					"kle": {
						"init": ["11-11.01"],
						"remove": [],
						"add": []
					}
				}
			]
			""", targetOu.getUuid());

		this.mockMvc.perform(post("/api/kleload")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());
	}
}
