package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationOrgUnit;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.stream.Collectors;

@RequireAdministratorRole
@Controller
public class OldAttestationPdfController {

	@Autowired
	private OrgUnitService orgUnitService;

	@GetMapping("/ui/admin/attestations/old")
	public String getAttestationsForAdmin(Model model) {
		model.addAttribute("ous", orgUnitService.getAll().stream().map(ou -> new AttestationOrgUnit(ou, false)).collect(Collectors.toList()));

		return "reports/list_old_attestation_pdfs";
	}
}
