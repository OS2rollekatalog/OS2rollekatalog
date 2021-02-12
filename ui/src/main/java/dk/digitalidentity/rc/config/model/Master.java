package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Master {
	private boolean enabled = true;
	private String url = "https://master.rollekatalog.dk";
}
