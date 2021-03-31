package dk.digitalidentity.rc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.OrgUnitAttestationPdfDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAttestationPdf;

@Service
public class OrgUnitAttestationPdfService {

	@Autowired
	private OrgUnitAttestationPdfDao orgUnitAttestationPdfDao;

	public OrgUnitAttestationPdf save(OrgUnit orgUnit, OrgUnitAttestationPdf attestationPdf) {
		if (orgUnit.getAttestationPdf() != null) {
			OrgUnitAttestationPdf existing = orgUnit.getAttestationPdf();
			existing.setPdf(attestationPdf.getPdf());
			return orgUnitAttestationPdfDao.save(existing);
		}

		attestationPdf = orgUnitAttestationPdfDao.save(attestationPdf);
		orgUnit.setAttestationPdf(attestationPdf);
		
		return attestationPdf;
	}
}
