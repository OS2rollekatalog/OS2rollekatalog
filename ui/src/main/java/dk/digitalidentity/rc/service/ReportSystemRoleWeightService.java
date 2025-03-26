package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRole;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentReportEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.util.NullSafe.nullSafe;

public class ReportSystemRoleWeightService {

    public static void addSystemRoleWeights(List<HistoryItSystem> itSystems, final List<UserRoleAssignmentReportEntry> result) {
        // First find weight pr. user-role
        final Map<Long, Long> userRoleIdWeight = itSystems.stream()
                .flatMap(its -> its.getHistoryUserRoles().stream())
                .collect(Collectors.toMap(HistoryUserRole::getUserRoleId, ur -> ur.getHistorySystemRoleAssignments().stream()
                                .mapToLong(sra -> findWeightForSystemRoleAssignment(ur.getHistoryItSystem(), sra))
                                .max().orElse(1))
                );
        final Map<String, List<UserRoleAssignmentReportEntry>> userEntries = result.stream().collect(
                Collectors.toMap(UserRoleAssignmentReportEntry::getUserId, Arrays::asList, (a, b) -> Stream.concat(a.stream(), b.stream()).toList())
        );
        result.forEach(entry -> {
            Long systemRoleWeight = userRoleIdWeight.get(entry.getRoleId());
            entry.setSystemRoleWeight(systemRoleWeight != null ? systemRoleWeight : 1L);
            entry.setItSystemResultWeight(findWeightForUser(userRoleIdWeight, userEntries.get(entry.getUserId()), entry));
        });
    }

    private static long findWeightForUser(final Map<Long, Long> userRoleIdWeight, final List<UserRoleAssignmentReportEntry> userEntries,
                                          final UserRoleAssignmentReportEntry entry) {
        return userEntries.stream()
                .filter(e -> e.getItSystem().equals(entry.getItSystem()))
                .map(e -> userRoleIdWeight.get(e.getRoleId()))
                .filter(Objects::nonNull)
                .mapToLong(e -> e)
                .max().orElse(1);
    }

    private static long findWeightForSystemRoleAssignment(final HistoryItSystem itSystems, final HistorySystemRoleAssignment sra) {
        return itSystems.getHistorySystemRoles().stream()
                .filter(sr -> sr.getSystemRoleId() == sra.getSystemRoleId() && sr.getWeight() != null)
                .mapToLong(HistorySystemRole::getWeight)
                .findFirst().orElse(1L);
    }

}
