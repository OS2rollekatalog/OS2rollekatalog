package dk.digitalidentity.rc.service.orgunit;

import dk.digitalidentity.rc.dao.model.OrgUnitNearestManager;
import dk.digitalidentity.rc.dao.model.OrgUnitNearestManager.ApprovingManager;
import dk.digitalidentity.rc.dao.projections.OrgUnitParentAndManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NearestManagerCalculator")
class NearestManagerCalculatorTest {
	private final List<OrgUnitParentAndManager> orgUnits = new ArrayList<>();

	@BeforeEach
	void buildOrganisation() {
		orgUnit("kommune", null, "chef");
		orgUnit("administration", "kommune", null);
		orgUnit("loen", "administration", null);
		orgUnit("boern-og-unge", "kommune", "mads");
		orgUnit("dagtilbud", "boern-og-unge", null);
		orgUnit("skoler", "boern-og-unge", "mads");
		orgUnit("skole-nord", "skoler", "nina");
		orgUnit("kultur", "kommune", "chef");
		orgUnit("teknik", "kommune", "ole");
		orgUnit("vej-og-park", "teknik", null);
		orgUnit("uden-leder", null, null);
		orgUnit("uden-leder-afdeling", "uden-leder", null);
	}

	@Nested
	@DisplayName("calculate")
	class Calculate {

		@Test
		@DisplayName("gives the same approving manager as the upward walk for every OU and receiver")
		void matchesUpwardWalk() {
			// ---- Given ---- //
			List<String> receivers = Arrays.asList("chef", "mads", "nina", "ole", "medarbejder", null);
			List<String> mismatches = new ArrayList<>();
			int approvals = 0;

			// ---- When ---- //
			Map<String, OrgUnitNearestManager> calculated = NearestManagerCalculator.calculate(orgUnits);

			// ---- Then ---- //
			for (OrgUnitParentAndManager orgUnit : orgUnits) {
				for (String receiver : receivers) {
					Optional<ApprovingManager> expected = upwardWalk(orgUnit.uuid(), receiver);
					Optional<ApprovingManager> actual = Optional.ofNullable(calculated.get(orgUnit.uuid()))
						.flatMap(row -> row.approvingManagerFor(receiver));
					if (!expected.equals(actual)) {
						mismatches.add(orgUnit.uuid() + " / " + receiver + ": expected " + expected + ", got " + actual);
					}
					approvals += expected.isPresent() ? 1 : 0;
				}
			}
			assertThat(approvals).isPositive();
			assertThat(mismatches).isEmpty();
		}

		@Test
		@DisplayName("skips a manager who manages several levels when finding the next manager")
		void nextManagerSkipsSameManager() {
			// ---- When ---- //
			OrgUnitNearestManager skoler = NearestManagerCalculator.calculate(orgUnits).get("skoler");

			// ---- Then ---- //
			assertThat(skoler.getNextManagerUuid()).isEqualTo("chef");
		}

		@Test
		@DisplayName("leaves out OUs without a manager anywhere above them")
		void noManagerNoRow() {
			// ---- When ---- //
			Map<String, OrgUnitNearestManager> calculated = NearestManagerCalculator.calculate(orgUnits);

			// ---- Then ---- //
			assertThat(calculated).doesNotContainKeys("uden-leder", "uden-leder-afdeling");
		}

		@Test
		@DisplayName("stops on a cyclic hierarchy instead of looping")
		void cyclicHierarchy() {
			// ---- Given ---- //
			orgUnits.clear();
			orgUnit("enhed-a", "enhed-b", null);
			orgUnit("enhed-b", "enhed-a", null);

			// ---- When ---- //
			Map<String, OrgUnitNearestManager> calculated = NearestManagerCalculator.calculate(orgUnits);

			// ---- Then ---- //
			assertThat(calculated).isEmpty();
		}
	}

	// the rule as a direct walk: from the OU upwards, the first manager who is not the receiver
	private Optional<ApprovingManager> upwardWalk(String ouUuid, String receiverUuid) {
		Map<String, OrgUnitParentAndManager> byUuid = orgUnits.stream()
			.collect(Collectors.toMap(OrgUnitParentAndManager::uuid, Function.identity()));
		OrgUnitParentAndManager current = byUuid.get(ouUuid);
		while (current != null) {
			if (current.managerUuid() != null && !Objects.equals(current.managerUuid(), receiverUuid)) {
				return Optional.of(new ApprovingManager(current.managerUuid(), current.uuid()));
			}
			current = current.parentUuid() == null ? null : byUuid.get(current.parentUuid());
		}
		return Optional.empty();
	}

	private void orgUnit(String uuid, String parentUuid, String managerUuid) {
		orgUnits.add(new OrgUnitParentAndManager(uuid, parentUuid, managerUuid));
	}
}
