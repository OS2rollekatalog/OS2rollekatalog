package dk.digitalidentity.rc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;

@Configuration
@EnableJpaRepositories(basePackages = { "dk.digitalidentity.rc.dao", "dk.digitalidentity.rc.log", "dk.digitalidentity.rc.attestation" }, repositoryFactoryBeanClass = JpaRepositoryFactoryBean.class)
public class JpaConfiguration {

}