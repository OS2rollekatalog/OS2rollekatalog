package dk.digitalidentity.rc.service.kombit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "navn", "vaerdi", "dynamisk", "dataafgraensningstypeEntityId", "dataafgraensningstypeNavn" })
@Getter
@Setter
public class KOMBITDataafgraensningsVaerdier {

	@JsonProperty("navn")
	public String navn;
	
	@JsonProperty("vaerdi")
	public String vaerdi;
	
	@JsonProperty("dynamisk")
	public Boolean dynamisk;
	
	@JsonProperty("dataafgraensningstypeEntityId")
	private String dataafgraensningstypeEntityId;

	@JsonProperty("dataafgraensningstypeNavn")
	private String dataafgraensningstypeNavn;
}