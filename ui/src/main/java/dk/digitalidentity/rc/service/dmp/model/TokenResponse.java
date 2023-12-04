package dk.digitalidentity.rc.service.dmp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenResponse {
	private String scope;
	
    @JsonProperty("access_token")
	private String access_token;
    
    @JsonProperty("token_type")
	private String tokenType;
    
    @JsonProperty("expires_in")
	private int expiresIn;
}
