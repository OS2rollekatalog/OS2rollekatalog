package dk.digitalidentity.rc.dao.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;

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

	// TODO: we can drop this from the database at some point, not used any more
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

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinTable(name = "report_template_user", joinColumns = { @JoinColumn(name = "template_id") }, inverseJoinColumns = { @JoinColumn(name = "user_uuid") })
	private List<User> users;
}
