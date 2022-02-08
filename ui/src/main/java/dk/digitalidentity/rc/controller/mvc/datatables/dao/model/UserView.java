package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
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
	private String title;
	
	@Column
	private String orgunitUuid;

}
