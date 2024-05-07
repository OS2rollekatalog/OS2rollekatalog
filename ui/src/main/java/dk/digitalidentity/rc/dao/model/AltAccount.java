package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
