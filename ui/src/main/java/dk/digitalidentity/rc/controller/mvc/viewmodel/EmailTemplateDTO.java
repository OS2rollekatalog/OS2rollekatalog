package dk.digitalidentity.rc.controller.mvc.viewmodel;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailTemplateDTO {
	private long id;
	private String title;
	private String message;
	private String templateTypeText;
	private boolean enabled;
}
