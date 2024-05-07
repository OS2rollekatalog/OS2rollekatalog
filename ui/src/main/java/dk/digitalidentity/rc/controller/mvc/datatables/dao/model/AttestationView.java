package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "view_datatables_attestations")
public class AttestationView {

	@Id
	@Column
	private String uuid;

	@Column
	private String name;

	@Column
	private String manager;

	@Column
	private String lastAttestedBy;
	
	@Column
	private Date lastAttested;
	
	@Column
	private Date nextAttestation;
}
