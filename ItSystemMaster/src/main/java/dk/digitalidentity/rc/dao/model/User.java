package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "user")
public class User {

	@Id
	@Column(name = "id")
	private long id;

	@Column(name = "username")
	private String username;
	
	@Column(name = "password")
	private String password;
}
