package dk.digitalidentity.rc;

import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    @Bean
    @ServiceConnection
    @RestartScope
    public MySQLContainer<?> mariaDBContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mariadb:10.6.14")
                .asCompatibleSubstituteFor("mysql"));
    }

}
