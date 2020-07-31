package dk.digitalidentity.rc.controller.api.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OrgUnitAM {
	private String uuid;
	
	private String name;
	
	@JsonProperty("kle-performing")
	private List<String> klePerforming;
	
	@JsonProperty("kle-interest")
	private List<String> kleInterest;
	
	private List<UserAM> employees;

	private List<OrgUnitAM> children;
	
	private List<Long> itSystemIdentifiers;
	private String managerUuid;
}
