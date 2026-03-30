package dk.digitalidentity.rc.controller.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.test.AbstractApiTest;

/**
 * Test suite for Organisation API endpoints.
 * <p>
 * Tests API endpoints for importing and exporting organizational structures, including
 * organizational units and users. Supports multiple API versions (v1 deprecated, v2 deprecated,
 * v3 current), full imports, delta syncs, KLE code assignments, and domain-specific operations.
 * The API provides comprehensive organization synchronization capabilities for external systems.
 * </p>
 */
@DisplayName("Organisation API Tests")
public class OrganisationApiTest extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private KleService kleService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ORGANISATION.toString());
	}

	/**
	 * Tests that KLE codes can be overwritten for an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - KLE performing and interest codes can be set
	 * - Previous KLE assignments are replaced (not merged)
	 * - KLE assignments are persisted correctly
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully overwrite KLE codes for organizational unit")
	void testOverwriteKle() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		// Set up initial KLE codes that should be replaced
		Kle existingKle1 = new Kle();
		existingKle1.setCode("99.99.99");
		existingKle1.setName("Existing KLE 1");
		existingKle1.setActive(true);
		existingKle1.setParent("0");
		kleService.save(existingKle1);

		KLEMapping existingMapping = new KLEMapping();
		existingMapping.setCode("99.99.99");
		existingMapping.setAssignmentType(KleType.PERFORMING);
		existingMapping.setOrgUnit(orgUnit);
		orgUnit.getKles().add(existingMapping);
		orgUnitService.save(orgUnit);

		entityManager.flush();
		entityManager.clear();

		// Verify initial state
		OrgUnit orgUnitBefore = orgUnitService.getByUuid(orgUnit.getUuid());
		assertThat(orgUnitBefore.getKles())
			.extracting("code")
			.contains("99.99.99");

		// Create new KLE codes for the test
		Kle kle1 = new Kle();
		kle1.setCode("00.01.02");
		kle1.setName("Test KLE 1");
		kle1.setActive(true);
		kle1.setParent("0");
		kleService.save(kle1);

		Kle kle2 = new Kle();
		kle2.setCode("00.02.03");
		kle2.setName("Test KLE 2");
		kle2.setActive(true);
		kle2.setParent("0");
		kleService.save(kle2);

		Kle kle3 = new Kle();
		kle3.setCode("00.03.04");
		kle3.setName("Test KLE 3");
		kle3.setActive(true);
		kle3.setParent("0");
		kleService.save(kle3);

		Kle kle4 = new Kle();
		kle4.setCode("00.04.05");
		kle4.setName("Test KLE 4");
		kle4.setActive(true);
		kle4.setParent("0");
		kleService.save(kle4);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
           {
               "klePerforming": ["00.01.02", "00.02.03"],
               "kleInterest": ["00.03.04", "00.04.05"]
           }
           """;

		this.mockMvc.perform(post("/api/orgunit/kle/{uuid}", orgUnit.getUuid())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("organisation-kle-overwrite",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("uuid").description("The UUID of the org unit")
				),
				requestFields(
					fieldWithPath("klePerforming").type(JsonFieldType.ARRAY).description("List of KLE codes for performing tasks").optional(),
					fieldWithPath("kleInterest").type(JsonFieldType.ARRAY).description("List of KLE codes for areas of interest").optional()
				)
			));

		entityManager.flush();
		entityManager.clear();

		OrgUnit updatedOrgUnit = orgUnitService.getByUuid(orgUnit.getUuid());

		// Verify total count
		assertThat(updatedOrgUnit.getKles()).hasSize(4);

		// Verify KLE performing codes can be set
		assertThat(updatedOrgUnit.getKles())
			.filteredOn(mapping -> mapping.getAssignmentType() == KleType.PERFORMING)
			.extracting("code")
			.containsExactlyInAnyOrder("00.01.02", "00.02.03")
			.as("KLE performing codes should be set correctly");

		// Verify KLE interest codes can be set
		assertThat(updatedOrgUnit.getKles())
			.filteredOn(mapping -> mapping.getAssignmentType() == KleType.INTEREST)
			.extracting("code")
			.containsExactlyInAnyOrder("00.03.04", "00.04.05")
			.as("KLE interest codes should be set correctly");

		// Verify previous KLE assignments are replaced (not merged)
		assertThat(updatedOrgUnit.getKles())
			.extracting("code")
			.doesNotContain("99.99.99")
			.as("Previous KLE assignment should be replaced, not merged");
	}

	/**
	 * Tests that overwriting KLE for non-existent org unit returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid UUID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when overwriting KLE for non-existent org unit")
	void testOverwriteKle_NotFound() throws Exception {
		String requestBody = """
            {
                "klePerforming": ["00.01.02"],
                "kleInterest": ["00.03.04"]
            }
            """;

		this.mockMvc.perform(post("/api/orgunit/kle/{uuid}", "nonexistent-uuid")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that KLE assignments can be cleared with empty lists.
	 * <p>
	 * Verifies that:
	 * - Empty arrays clear existing KLE assignments
	 * - Endpoint returns HTTP 200 OK
	 * - All KLE codes are removed from the org unit
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully clear KLE codes with empty lists")
	void testOverwriteKle_EmptyLists() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		String requestBody = """
            {
                "klePerforming": [],
                "kleInterest": []
            }
            """;

		this.mockMvc.perform(post("/api/orgunit/kle/{uuid}", orgUnit.getUuid())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.clear();
		OrgUnit updatedOrgUnit = orgUnitService.getByUuid(orgUnit.getUuid());
		assertEquals(0, updatedOrgUnit.getKles().size());
	}

	/**
	 * Tests that v1 API endpoint is deprecated and returns 400.
	 * <p>
	 * Verifies that:
	 * - Old v1 endpoint returns HTTP 400 Bad Request
	 * - API version deprecation is enforced
	 * - Clients are forced to upgrade to newer versions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 for deprecated v1 endpoint")
	void testImportOrgUnits_V1_Deprecated() throws Exception {
		this.mockMvc.perform(post("/api/organisation")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	/**
	 * Tests that organizational structure can be imported via v3 API.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Organization units and users can be imported
	 * - Response includes counts of created, updated, and deleted entities
	 * - REST documentation is generated
	 * </p>
	 * <p>
	 * This is a full import that synchronizes the entire organizational structure. Meaning all users except this one
	 * we add gets deleted
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully import organizational structure via v3 API")
	void testImportOrgUnitsV3() throws Exception {
		String orgUnitUuid = "550e8400-e29b-41d4-a716-446655440001";
		String userUuid = "550e8400-e29b-41d4-a716-446655440002";
		String userExtUuid = "3ae1f89e-4e29-4b74-996d-a81c96f47ea4";
		String userId = "testuser1";

		String requestBody = String.format("""
           {
               "orgUnits": [
                   {
                       "uuid": "%s",
                       "name": "Test Org Unit",
                       "parentOrgUnitUuid": null,
                       "inheritKle": true,
                       "level": null,
                       "klePerforming": [],
                       "kleInterest": [],
                       "manager": null,
                       "titleIdentifiers": []
                   }
               ],
               "users": [
                   {
                       "extUuid": "%s",
                       "uuid": "%s",
                       "userId": "%s",
                       "name": "Test User",
                       "email": "test@example.com",
                       "phone": null,
                       "cpr": null,
                       "nemloginUuid": null,
                       "doNotInherit": false,
                       "disabled": false,
                       "positions": [
                           {
                               "orgUnitUuid": "%s",
                               "name": "Test Position"
                           }
                       ],
                       "functions": [],
                       "klePerforming": [],
                       "kleInterest": []
                   }
               ]
           }
           """, orgUnitUuid, userExtUuid, userUuid, userId, orgUnitUuid);

		this.mockMvc.perform(post("/api/organisation/v3")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.usersCreated").value(1))
			.andExpect(jsonPath("$.usersUpdated").value(0))
			.andExpect(jsonPath("$.usersDeleted").value(115))
			.andExpect(jsonPath("$.ousCreated").value(1))
			.andExpect(jsonPath("$.ousUpdated").value(0))
			.andExpect(jsonPath("$.ousDeleted").value(11))
			.andDo(document("organisation-import-v3",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					subsectionWithPath("orgUnits").description("Array of organization units to import"),
					subsectionWithPath("users").description("Array of users to import")
				),
				responseFields(
					fieldWithPath("usersCreated").type(JsonFieldType.NUMBER).description("Number of users created"),
					fieldWithPath("usersUpdated").type(JsonFieldType.NUMBER).description("Number of users updated"),
					fieldWithPath("usersDeleted").type(JsonFieldType.NUMBER).description("Number of users deleted"),
					fieldWithPath("ousCreated").type(JsonFieldType.NUMBER).description("Number of org units created"),
					fieldWithPath("ousUpdated").type(JsonFieldType.NUMBER).description("Number of org units updated"),
					fieldWithPath("ousDeleted").type(JsonFieldType.NUMBER).description("Number of org units deleted")
				)
			));

		entityManager.flush();
		entityManager.clear();

		OrgUnit createdOrgUnit = orgUnitService.getByUuid(orgUnitUuid);
		assertThat(createdOrgUnit).isNotNull();
		assertThat(createdOrgUnit.getName()).isEqualTo("Test Org Unit");

		User createdUser = userService.getByUserId(userId);
		assertThat(createdUser).isNotNull();
		assertThat(createdUser.getName()).isEqualTo("Test User");
		assertThat(createdUser.getEmail()).isEqualTo("test@example.com");
		assertThat(createdUser.getPositions()).hasSize(1);
		assertThat(createdUser.getPositions().stream().findFirst().get().getOrgUnit().getUuid()).isEqualTo(orgUnitUuid);
	}

	/**
	 * Tests that v3 import can be scoped to a specific domain.
	 * <p>
	 * Verifies that:
	 * - Domain parameter is supported
	 * - Import is scoped to specified domain
	 * - REST documentation includes domain parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully import organizational structure for specific domain")
	void testImportOrgUnitsV3_WithDomain() throws Exception {
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		String requestBody = """
            {
                "orgUnits": [
                    {
                        "uuid": "550e8400-e29b-41d4-a716-446655440010",
                        "name": "Root Organization",
                        "parentOrgUnitUuid": null,
                        "inheritKle": true,
                        "level": null,
                        "klePerforming": [],
                        "kleInterest": [],
                        "manager": null,
                        "titleIdentifiers": []
                    }
                ],
                "users": []
            }
            """;

		this.mockMvc.perform(post("/api/organisation/v3")
				.header("ApiKey", API_KEY)
				.param("domain", domain.getName())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("organisation-import-v3-with-domain",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				queryParameters(
					parameterWithName("domain").description("Domain name for the import (optional, uses primary domain if not specified)")
				)
			));

		entityManager.flush();
		entityManager.clear();
		OrgUnit createdOrgUnit = orgUnitService.getByUuid("550e8400-e29b-41d4-a716-446655440010");
		assertThat(createdOrgUnit).isNotNull();
		assertThat(createdOrgUnit.getName()).isEqualTo("Root Organization");
	}

	/**
	 * Tests that invalid domain parameter returns 404.
	 * <p>
	 * Verifies that:
	 * - Invalid domain name returns HTTP 404 Not Found
	 * - Domain validation works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 for invalid domain during import")
	void testImportOrgUnitsV3_InvalidDomain() throws Exception {
		String requestBody = """
            {
                "orgUnits": [
                    {
                        "uuid": "550e8400-e29b-41d4-a716-446655440011",
                        "name": "Root Org",
                        "parentOrgUnitUuid": null,
                        "inheritKle": true,
                        "level": null,
                        "klePerforming": [],
                        "kleInterest": [],
                        "manager": null,
                        "titleIdentifiers": []
                    }
                ],
                "users": []
            }
            """;

		this.mockMvc.perform(post("/api/organisation/v3")
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that users can be synchronized using delta import.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Only specified users are updated (delta sync)
	 * - Response includes counts of affected entities
	 * - REST documentation is generated
	 * </p>
	 * <p>
	 * Delta sync allows updating specific users without full organizational import.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully perform delta sync of users via v3 API")
	void testImportUsersDeltaV3() throws Exception {
		User existingUser = userService.getByUserId(username);
		String requestBody = String.format("""
       [
           {
               "extUuid": "%s",
               "uuid": "%s",
               "userId": "%s",
               "name": "Updated User Name",
               "email": "updated@example.com",
               "phone": null,
               "cpr": null,
               "nemloginUuid": null,
               "doNotInherit": false,
               "disabled": false,
               "positions": [],
               "functions": [],
               "klePerforming": [],
               "kleInterest": []
           }
       ]
       """, existingUser.getExtUuid(), existingUser.getUuid(), existingUser.getUserId());

		this.mockMvc.perform(post("/api/organisation/v3/delta")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.usersCreated").value(0))
			.andExpect(jsonPath("$.usersUpdated").value(1))
			.andExpect(jsonPath("$.usersDeleted").value(0))
			.andDo(document("organisation-delta-import-v3",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					subsectionWithPath("[]").description("Array of users to update (delta sync)")
				),
				responseFields(
					fieldWithPath("usersCreated").type(JsonFieldType.NUMBER).description("Number of users created"),
					fieldWithPath("usersUpdated").type(JsonFieldType.NUMBER).description("Number of users updated"),
					fieldWithPath("usersDeleted").type(JsonFieldType.NUMBER).description("Number of users deleted"),
					fieldWithPath("ousCreated").type(JsonFieldType.NUMBER).description("Number of org units created"),
					fieldWithPath("ousUpdated").type(JsonFieldType.NUMBER).description("Number of org units updated"),
					fieldWithPath("ousDeleted").type(JsonFieldType.NUMBER).description("Number of org units deleted")
				)
			));

		entityManager.flush();
		entityManager.clear();

		// Verify the specified user was updated
		User updatedUser = userService.getByUserId(username);
		assertThat(updatedUser).isNotNull();
		assertThat(updatedUser.getName()).isEqualTo("Updated User Name");
		assertThat(updatedUser.getEmail()).isEqualTo("updated@example.com");

		// Verify other users remain unchanged (delta sync behavior)
		User otherUserAfter = userService.getByUserId("jgu");
		assertThat(otherUserAfter).isNotNull();
		assertThat(otherUserAfter.getName())
			.isEqualTo("Jeremy Leon Gulow")
			.as("Other user's name should remain unchanged in delta sync");
	}

	/**
	 * Tests that delta sync can be scoped to a specific domain.
	 * <p>
	 * Verifies that:
	 * - Domain parameter is supported for delta sync
	 * - Delta sync is scoped to specified domain
	 * - REST documentation includes domain parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully perform delta sync for specific domain")
	void testImportUsersDeltaV3_WithDomain() throws Exception {
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		String requestBody = "[]";

		this.mockMvc.perform(post("/api/organisation/v3/delta")
				.header("ApiKey", API_KEY)
				.param("domain", domain.getName())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("organisation-delta-import-v3-with-domain",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				queryParameters(
					parameterWithName("domain").description("Domain name for the delta sync (optional)")
				)
			));
	}

	/**
	 * Tests that delta sync with invalid domain returns 404.
	 * <p>
	 * Verifies that:
	 * - Invalid domain name returns HTTP 404 Not Found for delta sync
	 * - Domain validation works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 for invalid domain during delta sync")
	void testImportUsersDeltaV3_InvalidDomain() throws Exception {
		String requestBody = "[]";

		this.mockMvc.perform(post("/api/organisation/v3/delta")
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that current organizational hierarchy can be exported.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all org units and users
	 * - Data can be retrieved for backup or synchronization
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully export current organizational hierarchy")
	void testGetOrgUnitsHierarchy() throws Exception {
		this.mockMvc.perform(get("/api/organisation/v3")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orgUnits").isArray())
			.andExpect(jsonPath("$.orgUnits").isNotEmpty())
			.andExpect(jsonPath("$.users").isArray())
			.andExpect(jsonPath("$.users").isNotEmpty())
			.andDo(document("organisation-export-v3",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					subsectionWithPath("orgUnits").description("Array of all organization units"),
					subsectionWithPath("users").description("Array of all users")
				)
			));
	}

	/**
	 * Tests that v2 API endpoint still works but is deprecated.
	 * <p>
	 * Verifies that:
	 * - v2 endpoint still functions for backward compatibility
	 * - Endpoint returns HTTP 200 OK
	 * - Clients should migrate to v3
	 * </p>
	 * <p>
	 * Note: v2 is deprecated and maintained only for backward compatibility.
	 * New implementations should use v3.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should accept v2 import but endpoint is deprecated")
	void testImportOrgUnitsV2_Deprecated() throws Exception {
		String requestBody = """
            {
                "orgUnits": [
                    {
                        "uuid": "550e8400-e29b-41d4-a716-446655440020",
                        "name": "V2 Root Org",
                        "parentOrgUnitUuid": null
                    }
                ],
                "users": []
            }
            """;

		this.mockMvc.perform(post("/api/organisation/v2")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		OrgUnit createdOrgUnit = orgUnitService.getByUuid("550e8400-e29b-41d4-a716-446655440020");
		assertThat(createdOrgUnit).isNotNull();
		assertThat(createdOrgUnit.getName()).isEqualTo("V2 Root Org");
	}
}
