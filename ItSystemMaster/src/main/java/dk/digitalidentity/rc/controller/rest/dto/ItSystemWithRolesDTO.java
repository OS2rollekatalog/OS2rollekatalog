package dk.digitalidentity.rc.controller.rest.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import dk.digitalidentity.rc.dao.model.SystemRole;
import lombok.Data;

@Data
public class ItSystemWithRolesDTO {
	private String masterId;

	private String name;

	@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
	private Date lastModified;

	private List<SystemRole> systemRoles;

}
