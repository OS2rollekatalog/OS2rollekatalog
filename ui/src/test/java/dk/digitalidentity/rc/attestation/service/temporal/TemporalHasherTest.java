package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporalHasherTest {

    @Data
    @Builder
    @AllArgsConstructor
    public static class TemporalHasherTestObject {
        private int id;
        @PartOfNaturalKey
        private String valueString;
        @PartOfNaturalKey
        private Long valueLong;
        @PartOfNaturalKey
        private List<Long> valueList;
    }

    @Test
    public void canHashEntityEquals() {
        // Check that two section with similar content results in same hash
        final var t1 = TemporalHasherTestObject.builder()
                .id(1)
                .valueLong(1L)
                .valueString("værdi")
                .valueList(Arrays.asList(1L, 2L, 3L))
                .build();
        final var t2 = TemporalHasherTestObject.builder()
                .id(2)
                .valueLong(1L)
                .valueString("værdi")
                .valueList(Arrays.asList(1L, 2L, 3L))
                .build();
        final var t1hash = TemporalHasher.hashEntity(t1);
        final var t2hash = TemporalHasher.hashEntity(t2);
        assertThat(t1hash).isEqualTo(t2hash);
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    public void changeToFieldShouldResultInDifferentHash() {
        // Check that changing a field annotated with PartOfNaturalKey will result in a new hash
        final var t1 = TemporalHasherTestObject.builder()
                .id(1)
                .valueLong(1L)
                .valueString("værdi")
                .valueList(Arrays.asList(1L, 2L, 3L))
                .build();
        final var t1hash = TemporalHasher.hashEntity(t1);
        t1.setValueLong(777L);
        assertThat(t1hash).isNotEqualTo(TemporalHasher.hashEntity(t1));
    }

    /**
     * The literals were produced by the hasher before identity versions existed. If one of these fails,
     * every stored row of that table will be versioned on the next nightly run.
     */
    @Nested
    @DisplayName("identity versions reproduce the hashes already stored")
    class IdentityVersions {

        private AttestationOuRoleAssignment ouAssignment(List<String> exceptedOuUuids) {
            return AttestationOuRoleAssignment.builder()
                    .roleId(20L)
                    .ouUuid("ou-uuid")
                    .roleGroupId(30L)
                    .responsibleCollectionId(40L)
                    .responsibleOuUuid("responsible-ou-uuid")
                    .titleUuids(List.of("title-uuid"))
                    .exceptedUserUuids(List.of("user-uuid"))
                    .functionUuids(List.of("function-uuid"))
                    .itSystemId(10L)
                    .assignedThroughType(AssignedThroughType.ORGUNIT)
                    .assignedThroughName("Enhed")
                    .assignedThroughUuid("ou-uuid")
                    .exceptedTitleUuids(List.of("excepted-title-uuid"))
                    .exceptedOuUuids(exceptedOuUuids)
                    .build();
        }

        @Test
        @DisplayName("version 1 of an org unit assignment ignores excepted org units and matches the stored hash")
        void ouAssignmentVersionOneMatchesStoredHash() {
            // ---- Given ---- //
            AttestationOuRoleAssignment assignment = ouAssignment(List.of("excepted-ou-uuid"));

            // ---- When ---- //
            String hash = TemporalHasher.hashEntity(assignment, 1);

            // ---- Then ---- //
            assertThat(hash).isEqualTo("7f209d55710423a40e251e04d3a639ad");
        }

        @Test
        @DisplayName("olderHashes of an org unit assignment contains the stored version 1 hash")
        void ouAssignmentOlderHashesContainStoredHash() {
            // ---- Given ---- //
            AttestationOuRoleAssignment assignment = ouAssignment(List.of());

            // ---- When ---- //
            List<String> olderHashes = TemporalHasher.olderHashes(assignment);

            // ---- Then ---- //
            assertThat(olderHashes).containsExactly("7f209d55710423a40e251e04d3a639ad");
        }

        @Test
        @DisplayName("user role assignments keep their stored hash")
        void userRoleAssignmentKeepsStoredHash() {
            // ---- Given ---- //
            AttestationUserRoleAssignment assignment = AttestationUserRoleAssignment.builder()
                    .userUuid("user-uuid")
                    .userRoleId(20L)
                    .roleGroupId(30L)
                    .itSystemId(10L)
                    .responsibleCollectionId(40L)
                    .roleOuUuid("ou-uuid")
                    .responsibleOuUuid("responsible-ou-uuid")
                    .assignedThroughType(AssignedThroughType.DIRECT)
                    .assignedThroughName("Enhed")
                    .assignedThroughUuid("ou-uuid")
                    .build();

            // ---- When ---- //
            String hash = TemporalHasher.hashEntity(assignment);

            // ---- Then ---- //
            assertThat(hash).isEqualTo("eda7cde132527e0acce4dd165016124e");
        }

        @Test
        @DisplayName("system role assignments keep their stored hash")
        void systemRoleAssignmentKeepsStoredHash() {
            // ---- Given ---- //
            AttestationSystemRoleAssignment assignment = AttestationSystemRoleAssignment.builder()
                    .userRoleId(20L)
                    .userRoleName("Jobfunktionsrolle")
                    .systemRoleId(50L)
                    .systemRoleName("Systemrolle")
                    .responsibleCollectionId(40L)
                    .itSystemId(10L)
                    .itSystemName("It-system")
                    .constraints(new HashSet<>())
                    .build();

            // ---- When ---- //
            String hash = TemporalHasher.hashEntity(assignment);

            // ---- Then ---- //
            assertThat(hash).isEqualTo("b9f661f76fed73742186111ba4407ef3");
        }
    }

}
