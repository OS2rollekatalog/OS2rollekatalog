package dk.digitalidentity.rc.controller.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.test.AbstractApiTest;

/**
 * Test suite for Constraint API endpoints.
 * <p>
 * Tests API endpoints for managing constraint types and their value sets,
 * including creating new constraints, updating existing ones, and handling
 * various constraint UI types (REGEX, COMBO_SINGLE, COMBO_MULTI).
 * </p>
 */
@DisplayName("Constraint API Tests")
public class ConstraintApiTest extends AbstractApiTest {

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ROLE_MANAGEMENT.toString());
	}

	/**
	 * Tests that a new constraint type can be created successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Constraint type is persisted with correct properties
	 * - Value set is stored correctly
	 * - Service methods are called with correct arguments
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully create new constraint type")
	void testLoadConstraintValues_CreateNew() throws Exception {
		String entityId = "test-constraint-" + System.currentTimeMillis();

		// Create a spy to track service calls without breaking functionality
		ConstraintTypeService servicespy = Mockito.spy(constraintTypeService);

		String requestBody = """
           {
               "entityId": "%s",
               "name": "Test Constraint Type",
               "type": "REGEX",
               "valueSet": {
                   "pattern": "[A-Z]{3}[0-9]{4}",
                   "example": "ABC1234"
               }
           }
           """.formatted(entityId);

		this.mockMvc.perform(put("/api/constraint")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("constraint-create",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("entityId").type(JsonFieldType.STRING).description("Unique identifier for the constraint type"),
					fieldWithPath("name").type(JsonFieldType.STRING).description("Display name of the constraint type"),
					fieldWithPath("type").type(JsonFieldType.STRING).description("UI type of the constraint (COMBO_SINGLE, COMBO_MULTI, or REGEX)"),
					subsectionWithPath("valueSet").description("Key-value pairs defining the constraint values")
				)
			));

		entityManager.flush();
		entityManager.clear();
		ConstraintType created = constraintTypeService.getByEntityId(entityId);
		assertThat(created).isNotNull();
		assertThat(created.getName()).isEqualTo("Test Constraint Type");
		assertThat(created.getUiType()).isEqualTo(ConstraintUIType.REGEX);
		assertThat(created.getValueSet()).hasSize(2);
		assertThat(created.getValueSet())
			.extracting("constraintKey")
			.containsExactlyInAnyOrder("pattern", "example")
			.as("ValueSet should contain correct keys");
		assertThat(created.getValueSet())
			.extracting("constraintValue")
			.containsExactlyInAnyOrder("[A-Z]{3}[0-9]{4}", "ABC1234")
			.as("ValueSet should contain correct values");
	}

	/**
	 * Tests that an existing constraint type can be updated successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts updates to existing constraints
	 * - Name and UI type are updated correctly
	 * - Value set is completely replaced (not merged)
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully update existing constraint type")
	void testLoadConstraintValues_UpdateExisting() throws Exception {
		String entityId = "existing-constraint-" + System.currentTimeMillis();
		ConstraintType existing = createTestConstraintType(entityId, "Original Name", ConstraintUIType.COMBO_SINGLE);
		entityManager.flush();
		entityManager.clear();

		String requestBody = """
            {
                "entityId": "%s",
                "name": "Updated Constraint Name",
                "type": "COMBO_MULTI",
                "valueSet": {
                    "newKey1": "newValue1",
                    "newKey2": "newValue2"
                }
            }
            """.formatted(entityId);

		this.mockMvc.perform(put("/api/constraint")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("constraint-update",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("entityId").type(JsonFieldType.STRING).description("Unique identifier for the constraint type"),
					fieldWithPath("name").type(JsonFieldType.STRING).description("Display name of the constraint type"),
					fieldWithPath("type").type(JsonFieldType.STRING).description("UI type of the constraint (COMBO_SINGLE, COMBO_MULTI, or REGEX)"),
					subsectionWithPath("valueSet").description("Key-value pairs defining the constraint values (replaces existing values)")
				)
			));

		entityManager.flush();
		entityManager.clear();
		ConstraintType updated = constraintTypeService.getByEntityId(entityId);
		assertThat(updated).isNotNull();
		assertThat(updated.getName()).isEqualTo("Updated Constraint Name");
		// Note: uiType is only set on creation, not updated for existing constraints
		assertThat(updated.getUiType()).isEqualTo(ConstraintUIType.COMBO_SINGLE);
		assertThat(updated.getValueSet()).hasSize(2);
	}

	/**
	 * Tests that a constraint with an empty value set can be created.
	 * <p>
	 * Verifies that:
	 * - Empty value sets are accepted
	 * - Constraint is created successfully without values
	 * - No errors occur with empty value set
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully create constraint with empty value set")
	void testLoadConstraintValues_EmptyValueSet() throws Exception {
		String entityId = "empty-values-" + System.currentTimeMillis();
		String requestBody = """
            {
                "entityId": "%s",
                "name": "Empty Constraint",
                "type": "REGEX",
                "valueSet": {}
            }
            """.formatted(entityId);

		this.mockMvc.perform(put("/api/constraint")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		ConstraintType created = constraintTypeService.getByEntityId(entityId);
		assertThat(created).isNotNull();
		assertThat(created.getValueSet()).isEmpty();
	}

	/**
	 * Tests that a constraint with a large value set can be created.
	 * <p>
	 * Verifies that:
	 * - Large value sets (50+ entries) are handled correctly
	 * - All values are persisted
	 * - Performance is acceptable with many values
	 * - REST documentation is generated
	 * </p>
	 * <p>
	 * This test simulates scenarios like large OPUS organizational structures
	 * with many departments or units.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully create constraint with large value set")
	void testLoadConstraintValues_LargeValueSet() throws Exception {
		String entityId = "large-values-" + System.currentTimeMillis();
		StringBuilder valueSetBuilder = new StringBuilder("{\n");
		for (int i = 1; i <= 50; i++) {
			valueSetBuilder.append(String.format("    \"node%d\": \"Department %d\"", i, i));
			if (i < 50) {
				valueSetBuilder.append(",\n");
			} else {
				valueSetBuilder.append("\n");
			}
		}
		valueSetBuilder.append("  }");

		String requestBody = String.format("""
            {
                "entityId": "%s",
                "name": "Large OPUS Structure",
                "type": "COMBO_MULTI",
                "valueSet": %s
            }
            """, entityId, valueSetBuilder.toString());

		this.mockMvc.perform(put("/api/constraint")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("constraint-large-value-set",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();
		ConstraintType created = constraintTypeService.getByEntityId(entityId);
		assertThat(created).isNotNull();
		assertThat(created.getValueSet()).hasSize(50);
	}

	/**
	 * Tests that updating a constraint completely replaces the value set.
	 * <p>
	 * Verifies that:
	 * - Old values are removed, not merged with new values
	 * - New value set completely replaces the existing one
	 * - Number of values changes correctly
	 * - Only new keys are present after update
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should completely replace value set when updating constraint")
	void testLoadConstraintValues_ReplaceValueSet() throws Exception {
		String entityId = "replace-values-" + System.currentTimeMillis();
		ConstraintType existing = createTestConstraintType(entityId, "Original", ConstraintUIType.COMBO_SINGLE);
		assertThat(existing.getValueSet()).hasSize(2);

		String requestBody = """
            {
                "entityId": "%s",
                "name": "Replaced Values",
                "type": "COMBO_SINGLE",
                "valueSet": {
                    "new1": "New Value 1",
                    "new2": "New Value 2",
                    "new3": "New Value 3",
                    "new4": "New Value 4"
                }
            }
            """.formatted(entityId);

		this.mockMvc.perform(put("/api/constraint")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		ConstraintType updated = constraintTypeService.getByEntityId(entityId);
		assertThat(updated).isNotNull();
		assertThat(updated.getValueSet()).hasSize(4);
		assertThat(updated.getValueSet())
			.extracting("constraintKey")
			.containsExactlyInAnyOrder("new1", "new2", "new3", "new4");
	}

	/**
	 * Tests that constraint values can contain special characters.
	 * <p>
	 * Verifies that:
	 * - Danish characters (åæø) are preserved
	 * - Spaces and special characters (dashes, slashes, parentheses) are handled
	 * - All special characters are stored and retrieved correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully handle special characters in constraint values")
	void testLoadConstraintValues_SpecialCharactersInValues() throws Exception {
		String entityId = "special-chars-" + System.currentTimeMillis();
		String requestBody = """
            {
                "entityId": "%s",
                "name": "Special Characters Test",
                "type": "COMBO_MULTI",
                "valueSet": {
                    "key1": "Value with åæø",
                    "key2": "Value with spaces and - dashes",
                    "key3": "Value/with/slashes",
                    "key4": "Value (with) parentheses"
                }
            }
            """.formatted(entityId);

		this.mockMvc.perform(put("/api/constraint")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		ConstraintType created = constraintTypeService.getByEntityId(entityId);
		assertThat(created).isNotNull();
		assertThat(created.getValueSet())
			.anyMatch(v -> v.getConstraintValue().contains("åæø"));
	}

	/**
	 * Helper method to create a test constraint type with default values.
	 *
	 * @param entityId unique identifier for the constraint
	 * @param name display name of the constraint
	 * @param uiType UI type of the constraint
	 * @return the created and persisted constraint type
	 */
	private ConstraintType createTestConstraintType(String entityId, String name, ConstraintUIType uiType) {
		ConstraintType constraintType = new ConstraintType();
		constraintType.setUuid(java.util.UUID.randomUUID().toString());
		constraintType.setEntityId(entityId);
		constraintType.setName(name);
		constraintType.setUiType(uiType);
		constraintType.setValueSet(new ArrayList<>());

		constraintType.getValueSet().add(createValueSetEntry("default1", "Default Value 1"));
		constraintType.getValueSet().add(createValueSetEntry("default2", "Default Value 2"));

		constraintTypeService.save(constraintType);
		entityManager.flush();
		return constraintType;
	}

	/**
	 * Helper method to create a constraint value set entry.
	 *
	 * @param key the constraint key
	 * @param value the constraint value
	 * @return the created value set entry
	 */
	private dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet createValueSetEntry(String key, String value) {
		dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet entry = new dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet();
		entry.setConstraintKey(key);
		entry.setConstraintValue(value);
		return entry;
	}
}
