package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

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
