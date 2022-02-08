package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class SecurityLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false)
	private Date timestamp;

	@Column(nullable = false)
	private long clientId;
	
	@Column(nullable = false)
	private String clientname;

	@Column(nullable = false)
	private String method;

	@Column(nullable = false)
	private String request;

	@Column(nullable = false)
	private String ipAddress;

}
