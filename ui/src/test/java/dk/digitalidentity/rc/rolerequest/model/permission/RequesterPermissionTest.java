package dk.digitalidentity.rc.rolerequest.model.permission;

import dk.digitalidentity.rc.config.RequesterPermissionConverter;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RequesterPermission")
class RequesterPermissionTest {

	@Nested
	@DisplayName("of")
	class Of {

		@Test
		@DisplayName("rejects INHERIT combined with another value")
		void rejectsInheritCombined() {
			// ---- Given ---- //
			List<RequestableBy> values = List.of(RequestableBy.INHERIT, RequestableBy.EMPLOYEE);

			// ---- When / Then ---- //
			assertThatThrownBy(() -> RequesterPermission.of(values)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("rejects NONE combined with another value")
		void rejectsNoneCombined() {
			// ---- Given ---- //
			List<RequestableBy> values = List.of(RequestableBy.NONE, RequestableBy.ADMIN);

			// ---- When / Then ---- //
			assertThatThrownBy(() -> RequesterPermission.of(values)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("treats an empty collection as INHERIT")
		void emptyIsInherit() {
			// ---- When ---- //
			RequesterPermission permission = RequesterPermission.of(List.of());

			// ---- Then ---- //
			assertThat(permission.isInherit()).isTrue();
		}
	}

	@Nested
	@DisplayName("global")
	class Global {

		@Test
		@DisplayName("rejects INHERIT")
		void rejectsInherit() {
			// ---- Given ---- //
			List<RequestableBy> values = List.of(RequestableBy.INHERIT);

			// ---- When / Then ---- //
			assertThatThrownBy(() -> RequesterPermission.global(values)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("treats an empty collection as NONE")
		void emptyIsNone() {
			// ---- When ---- //
			RequesterPermission permission = RequesterPermission.global(List.of());

			// ---- Then ---- //
			assertThat(permission.isNone()).isTrue();
		}
	}

	@Nested
	@DisplayName("RequesterPermissionConverter")
	class Converter {
		private final RequesterPermissionConverter converter = new RequesterPermissionConverter();

		@Test
		@DisplayName("reads a stored INHERIT combination as INHERIT")
		void inheritWinsOverOtherValues() {
			// ---- When ---- //
			RequesterPermission permission = converter.convertToEntityAttribute("NONE,INHERIT,EMPLOYEE");

			// ---- Then ---- //
			assertThat(permission.isInherit()).isTrue();
		}

		@Test
		@DisplayName("reads a stored NONE combination as NONE")
		void noneWinsOverSpecificValues() {
			// ---- When ---- //
			RequesterPermission permission = converter.convertToEntityAttribute("EMPLOYEE,NONE");

			// ---- Then ---- //
			assertThat(permission.isNone()).isTrue();
		}

		@Test
		@DisplayName("writes and reads back a set of values")
		void roundTrip() {
			// ---- Given ---- //
			RequesterPermission permission = RequesterPermission.of(List.of(RequestableBy.ADMIN, RequestableBy.EMPLOYEE));

			// ---- When ---- //
			RequesterPermission read = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(permission));

			// ---- Then ---- //
			assertThat(read).isEqualTo(permission);
		}
	}
}
