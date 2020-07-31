package dk.digitalidentity.rc.controller.api.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UserAM {
	private String uuid;
	private String user_id;
	private String name;
	private String phone;
	private String email;
	private String title;	
	private List<String> klePerforming;
	private List<String> kleInterest;
}
