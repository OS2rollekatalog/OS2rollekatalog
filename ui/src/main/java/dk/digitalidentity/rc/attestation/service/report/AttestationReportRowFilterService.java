package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.service.AttestationCachedItSystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

/**
 * This class will filter report rows, when the it-system is exempt or there is duplicate rows.
 */
@Slf4j
@Service
public class AttestationReportRowFilterService {

    @Autowired
    private AttestationCachedItSystemService attestationCachedItSystemService;

    public Predicate<RoleAssignmentReportRowDTO> filter() {
        return row -> !attestationCachedItSystemService.isItSystemExempt(row.getItSystemId());
    }


}
