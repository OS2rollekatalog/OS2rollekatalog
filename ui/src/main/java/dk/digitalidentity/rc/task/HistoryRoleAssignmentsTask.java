package dk.digitalidentity.rc.task;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class HistoryRoleAssignmentsTask {

	@Value("${scheduled.enabled:false}")
	private boolean runScheduled;
	
	@Value("${history.generation.enabled:false}")
	private boolean enabledFeature;
	
	@Value("${history.retention.period:180}")
	private Long retentionPeriod;
	
	@Value("${spring.datasource.url:}")
	private String dataSourceUrl;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 4 * * ?")
	// enable this to execute script at bootup
	// @Scheduled(fixedDelay = 60 * 60 * 1000)
	public void generateHistory() {
		if (!runScheduled || !enabledFeature) {
			return;
		}
		
		Date now = new Date();
		
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryOrganisation();");
			jdbcTemplate.update("CALL SP_InsertHistoryItSystems();");
			jdbcTemplate.update("CALL SP_InsertHistoryRoleAssignments();");
			jdbcTemplate.update("CALL SP_InsertHistoryKleAssignments();");
			jdbcTemplate.update("CALL SP_InsertHistoryOURoleAssignments();");
			jdbcTemplate.update("CALL SP_InsertHistoryTitleRoleAssignments();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryOrganisation;");
			jdbcTemplate.update("EXEC SP_InsertHistoryItSystems;");
			jdbcTemplate.update("EXEC SP_InsertHistoryRoleAssignments;");
			jdbcTemplate.update("EXEC SP_InsertHistoryKleAssignments;");
			jdbcTemplate.update("EXEC SP_InsertHistoryOURoleAssignments;");
			jdbcTemplate.update("EXEC SP_InsertHistoryTitleRoleAssignments;");
		}
		
		log.info("Generating historic role assignments took " + ((new Date().getTime() - now.getTime()) / 1000) + " seconds");
	}
	
	@Scheduled(cron = "0 0 5 * * ?")
	public void deleteAncientHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("DELETE FROM history_users WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_ous WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_kle_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_it_systems WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_managers WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_ou_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_title_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_titles WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
		}
		else {
			jdbcTemplate.update("DELETE FROM history_users WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_ous WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_role_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_kle_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_it_systems WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_managers WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_ou_role_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_title_role_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_titles WHERE dato < GETDATE() - " + retentionPeriod + ";");
		}
	}
}
