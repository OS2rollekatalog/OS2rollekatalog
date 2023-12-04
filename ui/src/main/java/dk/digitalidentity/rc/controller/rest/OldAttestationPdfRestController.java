package dk.digitalidentity.rc.controller.rest;

import java.io.IOException;
import java.text.SimpleDateFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAttestationPdf;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.extern.slf4j.Slf4j;

@RequireAdministratorRole
@Slf4j
@RestController
public class OldAttestationPdfRestController {

	@Autowired
	private OrgUnitService orgUnitService;

	@GetMapping("/rest/admin/attestations/old/{uuid}/download")
	public ResponseEntity<?> downloadAdmin(@PathVariable("uuid") String uuid) throws IOException {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + uuid);

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		OrgUnitAttestationPdf ouap = orgUnit.getAttestationPdf();
		if (ouap == null) {
			log.warn("Unable to fetch OrgUnitAttestationPdf from OrgUnit with uuid: " + uuid);

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		StringBuilder fileNameBuilder = new StringBuilder();
		fileNameBuilder.append(sdf.format(orgUnit.getLastAttested()));
		fileNameBuilder.append(" - ");
		fileNameBuilder.append(orgUnit.getName());
		fileNameBuilder.append(" - ");
		fileNameBuilder.append("attesteringsrapport.pdf");
		String fileName = fileNameBuilder.toString();

		byte[] bytes = ouap.getPdf();
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		httpHeaders.setContentLength(bytes.length);
		httpHeaders.setContentDispositionFormData("attachment", fileName);

		return new ResponseEntity<>(bytes, httpHeaders, HttpStatus.OK);
	}

}
