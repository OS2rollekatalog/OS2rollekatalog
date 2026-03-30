package dk.digitalidentity.rc.controller.api.v1;

import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for Title API endpoints.
 * <p>
 * Tests API endpoints for managing job titles (stillingsbetegnelser) in the organization.
 * The API supports retrieving all active titles and synchronizing the complete list of titles
 * via a "sync to source of truth" pattern where titles in the payload are activated/updated
 * and titles not in the payload are deactivated. This pattern ensures the system state matches
 * the external source without requiring explicit delete operations.
 * </p>
 */
@DisplayName("Title API Tests")
public class TitleApiTest extends AbstractApiTest {

	@Autowired
	private TitleService titleService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ORGANISATION.toString());
	}

	/**
	 * Tests that all active titles can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of active titles
	 * - Each title includes UUID and name
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all active titles")
	void testGetAllTitles() throws Exception {
		Title title1 = new Title();
		title1.setUuid(UUID.randomUUID().toString());
		title1.setName("Test Title 1");
		title1.setActive(true);
		titleService.save(title1);

		Title title2 = new Title();
		title2.setUuid(UUID.randomUUID().toString());
		title2.setName("Test Title 2");
		title2.setActive(true);
		titleService.save(title2);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/title")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("title-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of active titles"),
					fieldWithPath("[].uuid").type(JsonFieldType.STRING).description("UUID of the title"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the title")
				)
			));
	}

	/**
	 * Tests that new titles can be created successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - New titles are created with provided UUIDs and names
	 * - All submitted titles are persisted as active
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully create new titles")
	void testSaveTitles_CreateNew() throws Exception {
		String requestBody = """
            [
                {
                    "uuid": "550e8400-e29b-41d4-a716-446655440001",
                    "name": "New Title 1"
                },
                {
                    "uuid": "550e8400-e29b-41d4-a716-446655440002",
                    "name": "New Title 2"
                }
            ]
            """;

		this.mockMvc.perform(post("/api/title")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("title-save",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("[]").description("Array of titles to create or update"),
					fieldWithPath("[].uuid").type(JsonFieldType.STRING).description("UUID of the title"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the title")
				)
			));

		entityManager.flush();
		entityManager.clear();
		List<Title> activeTitles = titleService.getAll();
		assertTrue(activeTitles.stream().anyMatch(t -> t.getName().equals("New Title 1")), "New Title 1 should be created");
		assertTrue(activeTitles.stream().anyMatch(t -> t.getName().equals("New Title 2")), "New Title 2 should be created");
	}

	/**
	 * Tests that existing titles can be updated.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Existing title name is updated
	 * - Title remains active after update
	 * - UUID is used to identify existing titles
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully update existing title")
	void testSaveTitles_UpdateExisting() throws Exception {
		String titleUuid = UUID.randomUUID().toString();
		Title existingTitle = new Title();
		existingTitle.setUuid(titleUuid);
		existingTitle.setName("Old Title Name");
		existingTitle.setActive(true);
		titleService.save(existingTitle);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            [
                {
                    "uuid": "%s",
                    "name": "Updated Title Name"
                }
            ]
            """, titleUuid);

		this.mockMvc.perform(post("/api/title")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		Title updatedTitle = titleService.getAllIncludingInactive().stream()
			.filter(t -> t.getUuid().equals(titleUuid))
			.findFirst()
			.orElse(null);

		assertNotNull(updatedTitle, "Title should still exist");
		assertEquals("Updated Title Name", updatedTitle.getName(), "Title name should be updated");
		assertTrue(updatedTitle.isActive(), "Title should be active");
	}

	/**
	 * Tests that inactive titles can be reactivated.
	 * <p>
	 * Verifies that:
	 * - Previously inactive titles can be reactivated
	 * - Title name can be updated during reactivation
	 * - Title state changes from inactive to active
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully reactivate inactive title")
	void testSaveTitles_ReactivateInactive() throws Exception {
		String titleUuid = UUID.randomUUID().toString();
		Title inactiveTitle = new Title();
		inactiveTitle.setUuid(titleUuid);
		inactiveTitle.setName("Inactive Title");
		inactiveTitle.setActive(false);
		titleService.save(inactiveTitle);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            [
                {
                    "uuid": "%s",
                    "name": "Reactivated Title"
                }
            ]
            """, titleUuid);

		this.mockMvc.perform(post("/api/title")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		Title reactivatedTitle = titleService.getAllIncludingInactive().stream()
			.filter(t -> t.getUuid().equals(titleUuid))
			.findFirst()
			.orElse(null);

		assertNotNull(reactivatedTitle, "Title should exist");
		assertTrue(reactivatedTitle.isActive(), "Title should be reactivated");
		assertEquals("Reactivated Title", reactivatedTitle.getName(), "Title name should be updated");
	}

	/**
	 * Tests that titles not in payload are automatically deactivated.
	 * <p>
	 * Verifies that:
	 * - Titles included in payload remain active
	 * - Titles not included in payload are deactivated
	 * - Deactivated titles are not deleted (soft delete)
	 * - This implements a "sync to source of truth" pattern
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should deactivate titles not present in payload")
	void testSaveTitles_DeactivateNotInPayload() throws Exception {
		String keepUuid = UUID.randomUUID().toString();
		Title keepTitle = new Title();
		keepTitle.setUuid(keepUuid);
		keepTitle.setName("Keep This Title");
		keepTitle.setActive(true);
		titleService.save(keepTitle);

		String removeUuid = UUID.randomUUID().toString();
		Title removeTitle = new Title();
		removeTitle.setUuid(removeUuid);
		removeTitle.setName("Remove This Title");
		removeTitle.setActive(true);
		titleService.save(removeTitle);

		entityManager.flush();
		entityManager.clear();

		String requestBody = String.format("""
            [
                {
                    "uuid": "%s",
                    "name": "Keep This Title"
                }
            ]
            """, keepUuid);

		this.mockMvc.perform(post("/api/title")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		List<Title> allTitles = titleService.getAllIncludingInactive();

		Title keptTitle = allTitles.stream()
			.filter(t -> t.getUuid().equals(keepUuid))
			.findFirst()
			.orElse(null);

		Title deactivatedTitle = allTitles.stream()
			.filter(t -> t.getUuid().equals(removeUuid))
			.findFirst()
			.orElse(null);

		assertNotNull(keptTitle, "Kept title should exist");
		assertTrue(keptTitle.isActive(), "Kept title should be active");

		assertNotNull(deactivatedTitle, "Deactivated title should still exist");
		assertFalse(deactivatedTitle.isActive(), "Title not in payload should be deactivated");
	}

	/**
	 * Tests that empty payload deactivates all titles.
	 * <p>
	 * Verifies that:
	 * - Empty array in payload is accepted
	 * - All existing active titles are deactivated
	 * - No titles remain active after empty payload
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should deactivate all titles when empty payload is sent")
	void testSaveTitles_EmptyPayload() throws Exception {
		Title title1 = new Title();
		title1.setUuid(UUID.randomUUID().toString());
		title1.setName("Title To Deactivate 1");
		title1.setActive(true);
		titleService.save(title1);

		Title title2 = new Title();
		title2.setUuid(UUID.randomUUID().toString());
		title2.setName("Title To Deactivate 2");
		title2.setActive(true);
		titleService.save(title2);

		entityManager.flush();
		entityManager.clear();

		String requestBody = "[]";

		this.mockMvc.perform(post("/api/title")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		List<Title> activeTitles = titleService.getAll();

		assertEquals(0, activeTitles.size(), "All titles should be deactivated when empty payload is sent");
	}

	/**
	 * Tests that multiple operations can be performed in a single request.
	 * <p>
	 * Verifies that:
	 * - Creating new titles works
	 * - Updating existing titles works
	 * - Reactivating inactive titles works
	 * - Deactivating omitted titles works
	 * - All operations can be combined in one API call
	 * </p>
	 * <p>
	 * This test demonstrates the full synchronization pattern where a single
	 * API call can create, update, reactivate, and deactivate titles to match
	 * the external source of truth.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully perform mixed create, update, reactivate, and deactivate operations")
	void testSaveTitles_MixedOperations() throws Exception {
		String existingUuid = UUID.randomUUID().toString();
		Title existingTitle = new Title();
		existingTitle.setUuid(existingUuid);
		existingTitle.setName("Existing Title");
		existingTitle.setActive(true);
		titleService.save(existingTitle);

		String inactiveUuid = UUID.randomUUID().toString();
		Title inactiveTitle = new Title();
		inactiveTitle.setUuid(inactiveUuid);
		inactiveTitle.setName("Inactive Title");
		inactiveTitle.setActive(false);
		titleService.save(inactiveTitle);

		String toRemoveUuid = UUID.randomUUID().toString();
		Title toRemoveTitle = new Title();
		toRemoveTitle.setUuid(toRemoveUuid);
		toRemoveTitle.setName("To Remove Title");
		toRemoveTitle.setActive(true);
		titleService.save(toRemoveTitle);

		entityManager.flush();
		entityManager.clear();

		String newUuid = UUID.randomUUID().toString();
		String requestBody = String.format("""
            [
                {
                    "uuid": "%s",
                    "name": "Updated Existing Title"
                },
                {
                    "uuid": "%s",
                    "name": "Reactivated Title"
                },
                {
                    "uuid": "%s",
                    "name": "Brand New Title"
                }
            ]
            """, existingUuid, inactiveUuid, newUuid);

		this.mockMvc.perform(post("/api/title")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		List<Title> allTitles = titleService.getAllIncludingInactive();

		Title updated = allTitles.stream().filter(t -> t.getUuid().equals(existingUuid)).findFirst().orElse(null);
		assertNotNull(updated);
		assertEquals("Updated Existing Title", updated.getName());
		assertTrue(updated.isActive());

		Title reactivated = allTitles.stream().filter(t -> t.getUuid().equals(inactiveUuid)).findFirst().orElse(null);
		assertNotNull(reactivated);
		assertTrue(reactivated.isActive());

		Title newTitle = allTitles.stream().filter(t -> t.getUuid().equals(newUuid)).findFirst().orElse(null);
		assertNotNull(newTitle);
		assertTrue(newTitle.isActive());

		Title removed = allTitles.stream().filter(t -> t.getUuid().equals(toRemoveUuid)).findFirst().orElse(null);
		assertNotNull(removed);
		assertFalse(removed.isActive());
	}
}
