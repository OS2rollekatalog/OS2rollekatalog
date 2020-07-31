package dk.digitalidentity.rc.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:git.properties")
public class BuildInfoContributor implements InfoContributor {

	@Value(value = "${git.build.time}")
	private String gitBuildTime;

	@Override
	public void contribute(Builder builder) {
		builder.withDetail("buildTime", gitBuildTime);
	}
}
