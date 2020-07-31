package dk.digitalidentity.rc.service.kombit.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "brugersystemrolleUuid", "dataafgraensningsVaerdier" })
@Getter
@Setter
public class KOMBITBrugersystemrolleDataafgraensningerDTO {

	@JsonProperty("brugersystemrolleUuid")
	public String brugersystemrolleUuid;
	
	@JsonProperty("dataafgraensningsVaerdier")
	public List<KOMBITDataafgraensningsVaerdier> dataafgraensningsVaerdier = new ArrayList<>();
}