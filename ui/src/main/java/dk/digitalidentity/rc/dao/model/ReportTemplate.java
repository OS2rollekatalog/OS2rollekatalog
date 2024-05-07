package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
