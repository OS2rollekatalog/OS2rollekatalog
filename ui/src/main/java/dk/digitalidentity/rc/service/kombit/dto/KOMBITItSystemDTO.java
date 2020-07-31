package dk.digitalidentity.rc.service.kombit.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KOMBITItSystemDTO {
	private String uuid;
	private String navn;
	private String organisationNavn;
	private String organisationCvr;

	// That Z at the end should be a timezone (ZULU), but KOMBIT sends Z as text, so we ignore it
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	private Date changedDate;
}
