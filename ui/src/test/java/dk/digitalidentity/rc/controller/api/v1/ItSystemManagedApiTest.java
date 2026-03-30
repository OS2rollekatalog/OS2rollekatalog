package dk.digitalidentity.rc.controller.api.v1;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.payload.JsonFieldType;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.test.AbstractApiTest;

/**
 * Test suite for IT System Managed API endpoints.
 * <p>
 * Tests API endpoints for retrieving IT systems with API-managed role assignments.
 * These systems have the apiManagedRoleAssignments flag set to true, indicating that
 * their role assignments are controlled externally via the API rather than through
 * the standard UI workflows.
 * </p>
 */
@DisplayName("IT System Managed API Tests")
public class ItSystemManagedApiTest extends AbstractApiTest {

	@Autowired
	private ItSystemService itSystemService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ROLE_MANAGEMENT.toString());
	}

	/**
	 * Tests that all IT systems with API-managed role assignments can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of IT systems with apiManagedRoleAssignments=true
	 * - IT systems with apiManagedRoleAssignments=false are NOT included
	 * - Each system includes id, name, and associated user roles
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all IT systems with API-managed role assignments")
	void testGetAllManagedItSystems() throws Exception {
		// Create/get an IT system with API-managed role assignments = true
		ItSystem managedItSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No it-system found in test env"));

		managedItSystem.setApiManagedRoleAssignments(true);
		itSystemService.save(managedItSystem);

		// Create an IT system with API-managed role assignments = false
		ItSystem unmanagedItSystem = new ItSystem();
		unmanagedItSystem.setName("Unmanaged Test System");
		unmanagedItSystem.setIdentifier("unmanaged-test-" + System.currentTimeMillis());
		unmanagedItSystem.setApiManagedRoleAssignments(false);
		unmanagedItSystem.setSystemType(ItSystemType.SAML);
		itSystemService.save(unmanagedItSystem);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/itSystem/managed")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andExpect(jsonPath("$[?(@.itSystemId == " + managedItSystem.getId() + ")]").exists())
			.andExpect(jsonPath("$[?(@.itSystemId == " + unmanagedItSystem.getId() + ")]").doesNotExist())
			.andExpect(jsonPath("$[0].roles").isArray())
			.andDo(document("itsystem-managed-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of IT systems with API-managed role assignments"),
					fieldWithPath("[].itSystemId").type(JsonFieldType.NUMBER).description("Unique identifier of the IT system"),
					fieldWithPath("[].itSystemName").type(JsonFieldType.STRING).description("Name of the IT system"),
					fieldWithPath("[].roles").type(JsonFieldType.ARRAY).description("List of user roles for this IT system"),
					fieldWithPath("[].roles[].id").type(JsonFieldType.NUMBER).description("Unique identifier of the user role").optional(),
					fieldWithPath("[].roles[].name").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("[].roles[].identifier").type(JsonFieldType.STRING).description("Identifier of the user role").optional(),
					fieldWithPath("[].roles[].description").type(JsonFieldType.STRING).description("Description of the user role").optional()
				)
			));
	}

	/**
	 * Tests that only IT systems with apiManagedRoleAssignments flag are returned.
	 * <p>
	 * Verifies that:
	 * - Systems with apiManagedRoleAssignments=true are included
	 * - Systems with apiManagedRoleAssignments=false are excluded
	 * - Filtering logic works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should only return IT systems with API-managed flag enabled")
	void testGetAllManagedItSystems_OnlyManagedSystems() throws Exception {
		ItSystem managedSystem = new ItSystem();
		managedSystem.setName("Managed System");
		managedSystem.setSystemType(ItSystemType.MANUAL);
		managedSystem.setIdentifier("managed-system");
		managedSystem.setApiManagedRoleAssignments(true);
		itSystemService.save(managedSystem);

		ItSystem nonManagedSystem = new ItSystem();
		nonManagedSystem.setName("Non-Managed System");
		nonManagedSystem.setSystemType(ItSystemType.MANUAL);
		nonManagedSystem.setIdentifier("non-managed-system");
		nonManagedSystem.setApiManagedRoleAssignments(false);
		itSystemService.save(nonManagedSystem);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/itSystem/managed")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$[?(@.itSystemName == 'Managed System')]").exists())
			.andExpect(jsonPath("$[?(@.itSystemName == 'Non-Managed System')]").doesNotExist());
	}

	/**
	 * Tests that an empty array is returned when no managed IT systems exist.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK even with no results
	 * - Response is an empty array
	 * - No errors occur when no systems have apiManagedRoleAssignments=true
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return empty array when no managed IT systems exist")
	void testGetAllManagedItSystems_EmptyResult() throws Exception {
		List<ItSystem> allSystems = itSystemService.getAll();
		for (ItSystem system : allSystems) {
			if (system.isApiManagedRoleAssignments()) {
				system.setApiManagedRoleAssignments(false);
				itSystemService.save(system);
			}
		}

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/itSystem/managed")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isEmpty());
	}
}
