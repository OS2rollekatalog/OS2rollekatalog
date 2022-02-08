package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ADSyncResult {
	private long head;
	private long maxHead;
	private List<ADGroupAssignments> assignments;
}
