package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ReportTemplate {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private String name;

	@Column
	private boolean showUsers;

	@Column
	private boolean showTitles;

	@Column(name = "show_ous")
	private boolean showOUs;

	@Column
	private boolean showUserRoles;

	@Column(name = "show_kle")
	private boolean showKLE;

	@Column
	private boolean showItSystems;

	@Column
	private boolean showInactiveUsers;
	
	@Column
	private String managerFilter;
	
	@Column
	private String unitFilter;
	
	@Column
	private String itsystemFilter;
}
