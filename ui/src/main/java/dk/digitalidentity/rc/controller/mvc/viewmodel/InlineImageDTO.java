package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class InlineImageDTO {
	private boolean url;
	private boolean base64;
	private String cid;
	private String src;
}
