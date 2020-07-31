package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import dk.digitalidentity.rc.dao.model.PendingADGroupOperation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ADOperationsResult {
	private long head;
	private List<PendingADGroupOperation> operations;
}
