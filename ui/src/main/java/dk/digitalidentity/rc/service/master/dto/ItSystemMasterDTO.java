package dk.digitalidentity.rc.service.master.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItSystemMasterDTO {
	private String masterId;
	private String name;
	private List<SystemRoleMasterDTO> systemRoles;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm")
	private Date lastModified;
}
