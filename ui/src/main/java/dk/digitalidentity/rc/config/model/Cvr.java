package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Cvr {
    private boolean enabled = false;
    private String apiKey = "";
    private String baseUrl = "https://datafordeler.digital-identity.dk/proxy";
}
