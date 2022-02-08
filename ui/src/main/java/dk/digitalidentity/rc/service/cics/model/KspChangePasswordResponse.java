package dk.digitalidentity.rc.service.cics.model;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KspChangePasswordResponse {
	private boolean success;
	private String response;
	private HttpStatus http;
}
