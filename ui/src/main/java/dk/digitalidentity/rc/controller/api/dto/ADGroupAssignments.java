package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ADGroupAssignments {
	private String groupName;
	private List<String> sAMAccountNames;

	@JsonIgnore
	private long itSystemId;
	@JsonIgnore
	private int weight;
}
