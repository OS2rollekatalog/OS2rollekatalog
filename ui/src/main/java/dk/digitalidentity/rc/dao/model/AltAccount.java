package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "users_alt_accounts")
@Getter
@Setter
public class AltAccount {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_uuid")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column
	private AltAccountType accountType;
	
	@Column
	private String accountUserId;
}
