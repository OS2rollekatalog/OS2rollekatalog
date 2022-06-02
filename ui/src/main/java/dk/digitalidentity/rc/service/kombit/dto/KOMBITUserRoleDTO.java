package dk.digitalidentity.rc.service.kombit.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "uuid", "navn", "entityId", "brugersystemrolleDataafgraensninger", "beskrivelse", "delegeretTilCvr", "organisationCvr", "version" })
@Getter
@Setter
public class KOMBITUserRoleDTO {

	@JsonProperty("uuid")
	public String uuid;
	
	@JsonProperty("navn")
	public String navn;
	
	@JsonProperty("entityId")
	public String entityId;
	
	@JsonProperty("brugersystemrolleDataafgraensninger")
	public List<KOMBITBrugersystemrolleDataafgraensningerDTO> brugersystemrolleDataafgraensninger = new ArrayList<>();
	
	@JsonProperty("beskrivelse")
	public String beskrivelse;
	
	@JsonProperty("delegeretTilCvr")
	public String delegeretTilCvr;
	
	@JsonProperty("organisationCvr")
	public String organisationCvr;
	
	@JsonProperty("version")
	public Integer version;
	
	public void setBeskrivelse(String input) {
		if (!StringUtils.hasLength(input)) {
			this.beskrivelse = "";
		}
		else if (input.length() > 200) {
			this.beskrivelse = input.substring(0, 200);
		}
		else {
			this.beskrivelse = input;
		}
	}
}