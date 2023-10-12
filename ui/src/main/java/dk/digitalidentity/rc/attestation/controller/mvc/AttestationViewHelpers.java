package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.dao.model.OrgUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AttestationViewHelpers {

    public static String buildBreadcrumbs(OrgUnit orgUnit) {
        OrgUnit current = orgUnit;
        List<String> names = new ArrayList<>();
        while (current != null) {
            names.add(current.getName());
            current = current.getParent();
        }
        Collections.reverse(names);

        return String.join(" > ", names);
    }

}
