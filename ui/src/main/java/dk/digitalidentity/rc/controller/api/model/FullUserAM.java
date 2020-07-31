package dk.digitalidentity.rc.controller.api.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FullUserAM {
	private String uuid;
	private String user_id;
	private String name;
	private String email;
	private String phone;
	private List<PositionAM> positions;
	
	@JsonProperty("kle-performing")
	private List<String> klePerforming;
	
	@JsonProperty("kle-interest")
	private List<String> kleInterest;
}
