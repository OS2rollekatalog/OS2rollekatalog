package dk.digitalidentity.rc.dao.model;

import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

	@Column
	private boolean showNegativeRoles;

	// TODO: we can drop this from the database at some point, not used any more
	@Column(name = "show_kle")
	private boolean showKLE;

	@Column
	private boolean showItSystems;

	@Column
	private boolean showInactiveUsers;

	@Column
	private boolean showSystemRoles;

	@Column
	private boolean showOrgUnitAllocations;

	@Column
	private String managerFilter;
	
	@Column
	private String unitFilter;
	
	@Column
	private String itsystemFilter;

	@OneToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "report_template_user", joinColumns = { @JoinColumn(name = "template_id") }, inverseJoinColumns = { @JoinColumn(name = "user_uuid") })
	private List<User> users;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "report_template_column", joinColumns = @JoinColumn(name = "template_id"))
	private Set<ReportTemplateColumnSelection> selectedColumns;

	@Embeddable
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class ReportTemplateColumnSelection {
		@Column(name = "content_type")
		private String contentType;

		@Column(name = "column_key")
		private String columnKey;
	}
}
