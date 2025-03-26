package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class will take a giant list of {@link dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment} id's
 * and then enables paginating through the AttestationUserRoleAssignment entities, this is will save memory when created attestation
 * reports.
 */
@Slf4j
public class AttestationReportPaginator {
    private static final int PAGE_SIZE = 1000;
    private final AttestationReportService service;
    private final List<List<Long>> rowIdPages;
    private final LocalDate since;
    private final LocalDate to;
    private final Predicate<RoleAssignmentReportRowDTO> rowFilter;
    private int currentPage = 0;

    public AttestationReportPaginator(final AttestationReportService service, final List<Long> rowIdPages,
                                      final LocalDate since, final LocalDate to, final Predicate<RoleAssignmentReportRowDTO> rowFilter) {
        this.service = service;
        this.rowIdPages = ListUtils.partition(rowIdPages, PAGE_SIZE);
        this.since = since;
        this.to = to;
        this.rowFilter = rowFilter;
    }

    public boolean hasNext() {
        return currentPage < rowIdPages.size();
    }

    public List<RoleAssignmentReportRowDTO> next() {
        if (currentPage >= rowIdPages.size()) {
            return Collections.emptyList();
        }
        log.info("Current page {} of {}", currentPage, rowIdPages.size());
        return service.getUserRoleRows(since, to, rowIdPages.get(currentPage++)).stream()
                .filter(rowFilter)
                .collect(Collectors.toList());
    }
}
