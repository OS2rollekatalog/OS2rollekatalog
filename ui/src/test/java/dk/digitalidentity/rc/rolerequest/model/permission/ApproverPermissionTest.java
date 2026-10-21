package dk.digitalidentity.rc.rolerequest.model.permission;

import dk.digitalidentity.rc.config.ApproverPermissionConverter;
import dk.digitalidentity.rc.config.RequestApproverOptionConverter;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApproverPermission")
class ApproverPermissionTest {

	@Nested
	@DisplayName("of")
	class Of {

		@Test
		@DisplayName("rejects AUTOMATIC combined with another value")
		void rejectsAutomaticCombined() {
			// ---- Given ---- //
			List<ApprovableBy> values = List.of(ApprovableBy.AUTOMATIC, ApprovableBy.AUTHORIZED);

			// ---- When / Then ---- //
			assertThatThrownBy(() -> ApproverPermission.of(values)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("treats an empty collection as nobody")
		void emptyIsNobody() {
			// ---- When ---- //
			ApproverPermission permission = ApproverPermission.of(List.of());

			// ---- Then ---- //
			assertThat(permission.isNobody()).isTrue();
		}
	}

	@Nested
	@DisplayName("global")
	class Global {

		@Test
		@DisplayName("rejects INHERIT")
		void rejectsInherit() {
			// ---- Given ---- //
			List<ApprovableBy> values = List.of(ApprovableBy.INHERIT);

			// ---- When / Then ---- //
			assertThatThrownBy(() -> ApproverPermission.global(values)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	@DisplayName("ApproverPermissionConverter")
	class EntityConverter {
		private final ApproverPermissionConverter converter = new ApproverPermissionConverter();

		@Test
		@DisplayName("reads NULL as INHERIT")
		void nullIsInherit() {
			// ---- When ---- //
			ApproverPermission permission = converter.convertToEntityAttribute(null);

			// ---- Then ---- //
			assertThat(permission.isInherit()).isTrue();
		}

		@Test
		@DisplayName("reads an empty value as nobody")
		void emptyIsNobody() {
			// ---- When ---- //
			ApproverPermission permission = converter.convertToEntityAttribute("");

			// ---- Then ---- //
			assertThat(permission.isNobody()).isTrue();
		}

		@Test
		@DisplayName("reads a stored AUTOMATIC combination as AUTOMATIC")
		void automaticWinsOverSpecificValues() {
			// ---- When ---- //
			ApproverPermission permission = converter.convertToEntityAttribute("MANAGERORSUBSTITUTE,AUTOMATIC");

			// ---- Then ---- //
			assertThat(permission.isAutomatic()).isTrue();
		}
	}

	@Nested
	@DisplayName("RequestApproverOptionConverter")
	class RequestConverter {
		private final RequestApproverOptionConverter converter = new RequestApproverOptionConverter();

		@Test
		@DisplayName("reads NULL as nobody, never INHERIT")
		void nullIsNobody() {
			// ---- When ---- //
			ApproverPermission permission = converter.convertToEntityAttribute(null);

			// ---- Then ---- //
			assertThat(permission.isNobody()).isTrue();
		}
	}
}
