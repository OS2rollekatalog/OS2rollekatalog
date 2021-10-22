package dk.digitalidentity.rc.controller.api;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.ConstraintValuesDTO;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;

@RequireApiRoleManagementRole
@RestController
@RequestMapping("/api")
public class ConstraintApi {
	
	@Autowired
	private ConstraintTypeService constraintTypeService;

	// TODO: actually not used any more...
	// TODO: document this API endpoint (used by Favrskov to load OPUS "knuder" as constraints)
	@PutMapping(value = "/constraint")
	public ResponseEntity<?> loadConstraintValues(@RequestBody ConstraintValuesDTO payload) {
		ConstraintType constraintType = constraintTypeService.getByEntityId(payload.getEntityId());
		if (constraintType == null) {
			constraintType = new ConstraintType();
			constraintType.setUuid(UUID.randomUUID().toString());
			constraintType.setEntityId(payload.getEntityId());
			constraintType.setUiType(ConstraintUIType.valueOf(payload.getType()));
		}
		
		constraintType.setName(payload.getName());
		constraintType.setValueSet(new ArrayList<ConstraintTypeValueSet>());

		for (String key : payload.getValueSet().keySet()) {
			ConstraintTypeValueSet entry = new ConstraintTypeValueSet();
			entry.setConstraintKey(key);
			entry.setConstraintValue(payload.getValueSet().get(key));
			
			constraintType.getValueSet().add(entry);			
		}

		constraintTypeService.save(constraintType);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
