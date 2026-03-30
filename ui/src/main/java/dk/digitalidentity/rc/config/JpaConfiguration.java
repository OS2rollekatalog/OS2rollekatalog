package dk.digitalidentity.rc.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
	"dk.digitalidentity.rc.dao",
	"dk.digitalidentity.rc.log",
	"dk.digitalidentity.rc.attestation",
	"dk.digitalidentity.rc.rolerequest.dao"
})
@EntityScan(basePackages = {"dk.digitalidentity"})
public class JpaConfiguration {

}
