package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ClientIntegrationType;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.rc.service.FunctionService;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Function API V2 Tests")
public class FunctionApiV2Test extends AbstractApiTest {

	@Autowired
	private FunctionService functionService;

	@Autowired
	private ClientService clientService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of("ORGANISATION");
	}

	@BeforeEach
	@Override
	public void setUp(RestDocumentationContextProvider restDocumentation) throws Exception {
		super.setUp(restDocumentation);

		Client testClient = clientService.getClientByApiKey(API_KEY);
		if (testClient == null) {
			testClient = new Client();
			testClient.setName("Test Function Client");
			testClient.setApiKey(API_KEY);
			testClient.setAccessRole(AccessRole.ADMINISTRATOR);
			testClient.setVersionStatus(VersionStatusEnum.UNKNOWN);
			testClient.setClientIntegrationType(ClientIntegrationType.GENERIC);
			testClient = clientService.save(testClient);
		}

		entityManager.flush();
		entityManager.clear();
	}

	// -------------------------------------------------------------------------
	// GET /api/v2/function
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("Should return empty list when no active functions exist")
	void testGetAll_Empty() throws Exception {
		this.mockMvc.perform(get("/api/v2/function")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isEmpty())
			.andDo(document("function-v2-get-all-empty",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));
	}

	@Test
	@DisplayName("Should return all active functions")
	void testGetAll() throws Exception {
		Function f = new Function();
		f.setUuid(UUID.randomUUID().toString());
		f.setName("Test Function");
		f.setActive(true);
		functionService.save(f);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/v2/function")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$[0].name").value("Test Function"))
			.andDo(document("function-v2-get-all",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));
	}

	@Test
	@DisplayName("Should not return inactive functions")
	void testGetAll_ExcludesInactive() throws Exception {
		Function f = new Function();
		f.setUuid(UUID.randomUUID().toString());
		f.setName("Inactive Function");
		f.setActive(false);
		functionService.save(f);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/v2/function")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	// -------------------------------------------------------------------------
	// POST /api/v2/function
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("Should create a new function")
	void testCreate() throws Exception {
		String requestBody = """
            {
                "name": "New Function"
            }
            """;

		this.mockMvc.perform(post("/api/v2/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("New Function"))
			.andExpect(jsonPath("$.uuid").isNotEmpty())
			.andDo(document("function-v2-create",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();

		Function saved = functionService.findByName("New Function").orElse(null);
		assertNotNull(saved);
		assertTrue(saved.isActive());
	}

	@Test
	@DisplayName("Should reactivate an existing inactive function on create")
	void testCreate_ReactivatesInactive() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Function existing = new Function();
		existing.setUuid(uuid);
		existing.setName("Reactivated Function");
		existing.setActive(false);
		functionService.save(existing);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            {
                "uuid": "%s",
                "name": "Reactivated Function"
            }
            """, uuid);

		this.mockMvc.perform(post("/api/v2/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uuid").value(uuid));

		entityManager.flush();
		entityManager.clear();

		Function reactivated = functionService.findByUuid(uuid).orElseThrow();
		assertTrue(reactivated.isActive());
	}

	@Test
	@DisplayName("Should persist a client supplied UUID when creating a new function")
	void testCreate_KeepsClientSuppliedUuid() throws Exception {
		String uuid = UUID.randomUUID().toString();

		String requestBody = String.format("""
            {
                "uuid": "%s",
                "name": "Source System Function"
            }
            """, uuid);

		this.mockMvc.perform(post("/api/v2/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uuid").value(uuid));

		entityManager.flush();
		entityManager.clear();

		Function saved = functionService.findByUuid(uuid).orElseThrow();
		assertEquals("Source System Function", saved.getName());
		assertTrue(saved.isActive());
	}

	@Test
	@DisplayName("Should rename a function matched by UUID on create")
	void testCreate_RenamesFunctionMatchedByUuid() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Function existing = new Function();
		existing.setUuid(uuid);
		existing.setName("Old Name");
		existing.setActive(true);
		functionService.save(existing);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            {
                "uuid": "%s",
                "name": "New Name"
            }
            """, uuid);

		this.mockMvc.perform(post("/api/v2/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uuid").value(uuid))
			.andExpect(jsonPath("$.name").value("New Name"));

		entityManager.flush();
		entityManager.clear();

		assertEquals("New Name", functionService.findByUuid(uuid).orElseThrow().getName());
		assertEquals(1, functionService.getAllIncludingInactive().size());
	}

	@Test
	@DisplayName("Should not create a duplicate when the name is already known under another UUID")
	void testCreate_MatchesByNameWhenUuidIsUnknown() throws Exception {
		String storedUuid = UUID.randomUUID().toString();
		Function existing = new Function();
		existing.setUuid(storedUuid);
		existing.setName("Existing Function");
		existing.setActive(false);
		functionService.save(existing);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            {
                "uuid": "%s",
                "name": "Existing Function"
            }
            """, UUID.randomUUID());

		this.mockMvc.perform(post("/api/v2/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uuid").value(storedUuid));

		entityManager.flush();
		entityManager.clear();

		assertTrue(functionService.findByUuid(storedUuid).orElseThrow().isActive());
		assertEquals(1, functionService.getAllIncludingInactive().size());
	}

	@Test
	@DisplayName("Should treat a blank UUID as no UUID on create")
	void testCreate_BlankUuidIsGenerated() throws Exception {
		String requestBody = """
            {
                "uuid": "",
                "name": "Blank Uuid Function"
            }
            """;

		this.mockMvc.perform(post("/api/v2/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uuid").isNotEmpty());

		entityManager.flush();
		entityManager.clear();

		assertTrue(functionService.findByName("Blank Uuid Function").isPresent());
	}

	@Test
	@DisplayName("Should return 400 when creating function without name")
	void testCreate_MissingName() throws Exception {
		this.mockMvc.perform(post("/api/v2/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	// -------------------------------------------------------------------------
	// PUT /api/v2/function/{uuid}
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("Should update an existing function's name")
	void testUpdate() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Function f = new Function();
		f.setUuid(uuid);
		f.setName("Old Name");
		f.setActive(true);
		functionService.save(f);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
            {
                "name": "Updated Name"
            }
            """;

		this.mockMvc.perform(put("/api/v2/function/{uuid}", uuid)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("function-v2-update",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("uuid").description("UUID of the function to update")
				)
			));

		entityManager.flush();
		entityManager.clear();

		Function updated = functionService.findByUuid(uuid).orElseThrow();
		assertEquals("Updated Name", updated.getName());
	}

	@Test
	@DisplayName("Should return 404 when updating non-existent function")
	void testUpdate_NotFound() throws Exception {
		String requestBody = """
            {
                "name": "Doesn't Matter"
            }
            """;

		this.mockMvc.perform(put("/api/v2/function/{uuid}", UUID.randomUUID().toString())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------------------
	// DELETE /api/v2/function/{uuid}
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("Should soft-delete a function by setting active to false")
	void testDelete() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Function f = new Function();
		f.setUuid(uuid);
		f.setName("To Be Deleted");
		f.setActive(true);
		functionService.save(f);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(delete("/api/v2/function/{uuid}", uuid)
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("function-v2-delete",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("uuid").description("UUID of the function to delete")
				)
			));

		entityManager.flush();
		entityManager.clear();

		Function deleted = functionService.findByUuid(uuid).orElseThrow();
		assertFalse(deleted.isActive());
	}

	@Test
	@DisplayName("Should return 404 when deleting non-existent function")
	void testDelete_NotFound() throws Exception {
		this.mockMvc.perform(delete("/api/v2/function/{uuid}", UUID.randomUUID().toString())
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------------------
	// POST /api/v2/function/sync
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("Should create new functions on sync")
	void testSync_CreateNew() throws Exception {
		String requestBody = """
            [
                { "name": "Function A" },
                { "name": "Function B" }
            ]
            """;

		this.mockMvc.perform(post("/api/v2/function/sync")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(2))
			.andDo(document("function-v2-sync",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();

		assertTrue(functionService.findByName("Function A").isPresent());
		assertTrue(functionService.findByName("Function B").isPresent());
	}

	@Test
	@DisplayName("Should deactivate functions not present in sync payload")
	void testSync_DeactivatesMissing() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Function existing = new Function();
		existing.setUuid(uuid);
		existing.setName("Should Be Deactivated");
		existing.setActive(true);
		functionService.save(existing);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
            [
                { "name": "New Function" }
            ]
            """;

		this.mockMvc.perform(post("/api/v2/function/sync")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();

		Function deactivated = functionService.findByUuid(uuid).orElseThrow();
		assertFalse(deactivated.isActive());
	}

	@Test
	@DisplayName("Should match existing function by name when no UUID is provided")
	void testSync_MatchByName() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Function existing = new Function();
		existing.setUuid(uuid);
		existing.setName("Existing Function");
		existing.setActive(true);
		functionService.save(existing);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
            [
                { "name": "Existing Function" }
            ]
            """;

		this.mockMvc.perform(post("/api/v2/function/sync")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].uuid").value(uuid));
	}

	@Test
	@DisplayName("Should persist client supplied UUIDs when syncing new functions")
	void testSync_KeepsClientSuppliedUuid() throws Exception {
		String uuid = UUID.randomUUID().toString();

		String requestBody = String.format("""
            [
                { "uuid": "%s", "name": "Source System Function" }
            ]
            """, uuid);

		this.mockMvc.perform(post("/api/v2/function/sync")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].uuid").value(uuid));

		entityManager.flush();
		entityManager.clear();

		Function saved = functionService.findByUuid(uuid).orElseThrow();
		assertEquals("Source System Function", saved.getName());
		assertTrue(saved.isActive());
	}

	@Test
	@DisplayName("Should rename a function matched by UUID instead of deactivating and recreating it")
	void testSync_RenameKeepsUuid() throws Exception {
		String uuid = UUID.randomUUID().toString();
		Function existing = new Function();
		existing.setUuid(uuid);
		existing.setName("Old Name");
		existing.setActive(true);
		functionService.save(existing);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            [
                { "uuid": "%s", "name": "New Name" }
            ]
            """, uuid);

		this.mockMvc.perform(post("/api/v2/function/sync")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].uuid").value(uuid));

		entityManager.flush();
		entityManager.clear();

		Function renamed = functionService.findByUuid(uuid).orElseThrow();
		assertEquals("New Name", renamed.getName());
		assertTrue(renamed.isActive());
		assertEquals(1, functionService.getAllIncludingInactive().size());
	}

	@Test
	@DisplayName("Should keep a function matched by name active when the caller supplies an unknown UUID")
	void testSync_KeepsFunctionMatchedByNameActive() throws Exception {
		String storedUuid = UUID.randomUUID().toString();
		Function existing = new Function();
		existing.setUuid(storedUuid);
		existing.setName("Existing Function");
		existing.setActive(true);
		functionService.save(existing);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            [
                { "uuid": "%s", "name": "Existing Function" }
            ]
            """, UUID.randomUUID());

		this.mockMvc.perform(post("/api/v2/function/sync")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].uuid").value(storedUuid));

		entityManager.flush();
		entityManager.clear();

		// a primary key cannot be reassigned, so the function keeps its own UUID and must stay active
		Function kept = functionService.findByUuid(storedUuid).orElseThrow();
		assertTrue(kept.isActive());
		assertEquals(1, functionService.getAllIncludingInactive().size());
	}

	@Test
	@DisplayName("Should return 400 when syncing with empty list")
	void testSync_EmptyList() throws Exception {
		this.mockMvc.perform(post("/api/v2/function/sync")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("[]"))
			.andExpect(status().isBadRequest());
	}
}
