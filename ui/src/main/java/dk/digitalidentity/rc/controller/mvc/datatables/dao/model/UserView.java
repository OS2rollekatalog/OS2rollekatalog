package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "view_datatables_users")
public class UserView {

	@Id
	@Column
	private String uuid;

	@Column
	private String name;
	
	@Column
	private String userId;

	@Column
	private String domain;
	
	@Column
	private String title;
	
	@Column
	private String orgunitUuid;

	@Column
	private boolean disabled;

}
