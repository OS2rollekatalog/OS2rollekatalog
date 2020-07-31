package dk.digitalidentity.rc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.model.Organisation;
import dk.digitalidentity.rc.config.model.Titles;
import lombok.Getter;
import lombok.Setter;

// TODO: we are starting the new configuration stuff here
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "rc")
public class RoleCatalogueConfiguration {
	private Titles titles = new Titles();
	private Organisation organisation = new Organisation();
}
