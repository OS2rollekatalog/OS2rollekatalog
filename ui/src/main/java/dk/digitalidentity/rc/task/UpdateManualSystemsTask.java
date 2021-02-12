package dk.digitalidentity.rc.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.LocaleUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingManualUpdate;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.EmailService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManualRolesService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
@Transactional
public class UpdateManualSystemsTask {
	private static final String localeString = "da_DK";

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private ManualRolesService manualRolesService;

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// run every 15 minutes
	@Scheduled(fixedDelay = 15 * 60 * 1000)
	public void processUsersFromWaitingTable() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		log.debug("Running scheduled job");

		List<PendingManualUpdate> pendingManualUpdates = manualRolesService.findAll();
		if (pendingManualUpdates == null || pendingManualUpdates.size() == 0) {
			return;
		}
		
		// remove duplicates (the duplicates are there to ensure we don't have a race condition
		// where we miss an update while running this scheduled task)
		List<PendingManualUpdate> filtered = new ArrayList<>();
		List<PendingManualUpdate> toRemove = new ArrayList<>();
		for (PendingManualUpdate update : pendingManualUpdates) {
			boolean alreadyFiltered = false;

			for (PendingManualUpdate f : filtered) {
				if (f.getUserId().equals(update.getUserId()) && f.getItSystemId() == update.getItSystemId()) {
					alreadyFiltered = true;
					break;
				}
			}

			if (!alreadyFiltered) {
				filtered.add(update);
			}
			else {
				toRemove.add(update);
			}
		}

		// wipe all duplicates
		manualRolesService.delete(toRemove);

		// Group list by ItSystem so that we send 1 email at a time
		Map<Long, List<PendingManualUpdate>> groupByItSystemMap = filtered.stream().collect(Collectors.groupingBy(PendingManualUpdate::getItSystemId));

		for (Long itSystemId : groupByItSystemMap.keySet()) {
			ItSystem itSystem = itSystemService.getById(itSystemId);
			String emailAddress = itSystem.getEmail();

			List<User> users = groupByItSystemMap.get(itSystemId).stream().map(PendingManualUpdate::getUserId).map(uid -> userService.getByUserId(uid)).collect(Collectors.toList());

			StringBuilder usersAndRoles = new StringBuilder();

			Locale locale = LocaleUtils.toLocale(localeString.replace('-', '_'));

			for (User user : users) {
				usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { user.getName() + " (" + user.getUserId() + ")" }, locale));

				usersAndRoles.append("<ul>");

				List<SystemRole> systemRoles = userService.getAllSystemRoles(user, Collections.singletonList(itSystem));
				if (systemRoles == null || systemRoles.size() == 0) {
					usersAndRoles.append(messageSource.getMessage("html.email.manual.message.noroles", null, locale));
				}
				else {
					for (SystemRole systemRole : systemRoles) {
						usersAndRoles.append("<li>" + systemRole.getName() + " &nbsp; (" + systemRole.getIdentifier() + ")</li>");
					}
				}

				usersAndRoles.append("</ul>");
			}

			String title = messageSource.getMessage("html.email.manual.title", new Object[] { itSystem.getName() }, locale);
			String message = messageSource.getMessage("html.email.manual.message.format", new Object[] { itSystem.getName(), usersAndRoles.toString() }, locale);

			try {
				emailService.sendMessage(emailAddress, title, message);
			}
			catch (Exception ex) {
				log.error("Exception occured while synchronizing manual ItSystem: " + itSystem + " Exception:" + ex.getMessage());

				// we just continue with the next one - someone has to fix this, and then perform a full sync
			}
		}

		manualRolesService.delete(filtered);
	}
}
