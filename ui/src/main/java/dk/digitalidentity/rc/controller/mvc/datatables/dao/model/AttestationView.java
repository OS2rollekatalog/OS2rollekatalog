package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

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
