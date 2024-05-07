package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

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

	@Column(nullable = true)
	private String clientVersion;

	@Column(nullable = true)
	private String tlsVersion;

	@Column(nullable = true)
	private String responseCode;
}
