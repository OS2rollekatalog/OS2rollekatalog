package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;

@Component
@EnableScheduling
public class JdbcSessionCleanupTask {

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;
    
    @Autowired
    private RoleCatalogueConfiguration configuration;
    
    @Scheduled(cron = "#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(10)}/10 * * * ?")
    public void cleanupJdbcSessions() {
    	if (configuration.getScheduled().isEnabled()) {
    		sessionRepository.cleanUpExpiredSessions();
    	}
    }
}
