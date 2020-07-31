package dk.digitalidentity.rc.controller.api.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FullOrgUnitAM {

	private String uuid;
	private String name;
	
	@JsonProperty("parent-ou-uuid")
	private String parentOrgUnitUuid;
	
	@JsonProperty("kle-performing")
	private List<String> klePerforming;
	
	@JsonProperty("kle-interest")
	private List<String> kleInterest;
	
	@JsonProperty("it-systems")
	private List<Long> itSystemIdentifiers;
	
	@JsonProperty("manager-uuid")
	private String managerUuid;
}
