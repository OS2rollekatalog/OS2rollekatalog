package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.LocaleUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemChange;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemChangeEventType;
import dk.digitalidentity.rc.util.DiffMatchPatch;
import dk.digitalidentity.rc.util.DiffMatchPatch.Diff;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ItSystemChangeUpdateService {
	private static final String localeString = "da_DK";

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private ItSystemChangeService itSystemChangeService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Transactional
	public void notifyAboutItSystems() {
		Locale locale = LocaleUtils.toLocale(localeString.replace('-', '_'));

		// Fetch email specified in settings, if none exists, delete everything
		String emailAddress = settingsService.getItSystemChangeEmail();
		if (!StringUtils.hasLength(emailAddress)) {
			itSystemChangeService.deleteAll();
			return;
		}

		// see if there are any changes
		List<ItSystemChange> itSystemChangeList = itSystemChangeService.findAll();
		if (itSystemChangeList == null || itSystemChangeList.size() == 0) {
			return;
		}

		// remove duplicates (the duplicates are there to ensure we don't have a race condition
		// where we miss an update while running this scheduled task)
		List<ItSystemChange> filtered = new ArrayList<>();
		List<ItSystemChange> toRemove = new ArrayList<>();
		for (ItSystemChange update : itSystemChangeList) {
			boolean alreadyFiltered = false;

			for (ItSystemChange f : filtered) {
				if (f.equals(update)) {
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
		itSystemChangeService.delete(toRemove);

		// Group list by ItSystem so that we send 1 email at a time
		Map<Long, List<ItSystemChange>> groupByItSystemMap = filtered.stream().collect(Collectors.groupingBy(ItSystemChange::getItSystemId));

		// Make a layout of email body
		for (Long itSystemId : groupByItSystemMap.keySet()) {
			StringBuilder body = new StringBuilder();
			ItSystem itSystem = itSystemService.getById(itSystemId);
			if (itSystem == null) {
				log.warn("Failed to send notification for it-system with id: " + itSystemId + " because it did not exist!");
				continue;
			}
			
			// hidden it-systems never emit notifications
			if (itSystem.isHidden()) {
				continue;
			}

			// Get added SystemRoles
			List<ItSystemChange> addedSystemRoles = groupByItSystemMap.get(itSystemId)
					.stream()
					.filter(change -> change.getEventType().equals(ItSystemChangeEventType.SYSTEM_ROLE_ADD))
					.collect(Collectors.toList());

			// Get modified SystemRoles
			List<ItSystemChange> modifiedSystemRoles = groupByItSystemMap.get(itSystemId)
					.stream()
					.filter(change -> change.getEventType().equals(ItSystemChangeEventType.SYSTEM_ROLE_MODIFY))
					.collect(Collectors.toList());

			// Get removed SystemRoles
			List<ItSystemChange> removedSystemRoles = groupByItSystemMap.get(itSystemId)
					.stream()
					.filter(change -> change.getEventType().equals(ItSystemChangeEventType.SYSTEM_ROLE_REMOVE))
					.collect(Collectors.toList());

			// TODO: should probably remove the it_system_modified record also...
			if (addedSystemRoles.size() == 0 && modifiedSystemRoles.size() == 0 && removedSystemRoles.size() == 0) {
				continue;
			}
			
			StringBuilder roleChanges = new StringBuilder();

			// find out if we're adding new itsystem or updating
			boolean createNewItSystem = groupByItSystemMap.get(itSystemId).stream().anyMatch(change -> change.getEventType().equals(ItSystemChangeEventType.ITSYSTEM_NEW));
			boolean updateItSystem = groupByItSystemMap.get(itSystemId).stream().anyMatch(change -> change.getEventType().equals(ItSystemChangeEventType.ITSYSTEM_MODIFY));

			if (addedSystemRoles.size() > 0) {
				StringBuilder rolesList = new StringBuilder();
				for (ItSystemChange systemRole : addedSystemRoles) {
					rolesList.append(div(dt("Navn")+dd(systemRole.getSystemRoleName())));
				}

				roleChanges.append(messageSource.getMessage("html.email.it.system.change.added.roles", new Object[] { rolesList.toString() }, locale));
			}
			
			if (modifiedSystemRoles.size() > 0) {
				StringBuilder rolesList = new StringBuilder();

				for (ItSystemChange systemRole : modifiedSystemRoles) {
					SystemRole currentSystemRole = systemRoleService.getById(systemRole.getSystemRoleId());
					if (currentSystemRole != null) {
						rolesList.append(div(dt("Navn") + dd(diff(systemRole.getSystemRoleName(), currentSystemRole.getName()))));
						rolesList.append(div(dt("Beskrivelse") + dd(diff(systemRole.getSystemRoleDescription(), currentSystemRole.getDescription()))));
	
						if (systemRole.isSystemRoleConstraintChanged()) {
							rolesList.append(div(dt("Dataafgrænsning") + dd("ændret")));
						}
					}
				}

				roleChanges.append(messageSource.getMessage("html.email.it.system.change.modified.roles", new Object[] { rolesList.toString() }, locale));
			}

			if (removedSystemRoles.size() > 0) {
				StringBuilder rolesList = new StringBuilder();
				for (ItSystemChange systemRole : removedSystemRoles) {
					rolesList.append(div(dt("Navn")+dd(systemRole.getSystemRoleName())));
				}

				roleChanges.append(messageSource.getMessage("html.email.it.system.change.removed.roles", new Object[] { rolesList.toString() }, locale));
			}

			if (createNewItSystem) {
				body.append(messageSource.getMessage("html.email.it.system.change.message.new", new Object[] { itSystem.getName(), roleChanges.toString() }, locale));
			}
			else {
				StringBuilder itSystemChanges = new StringBuilder();
				if (updateItSystem) {
					ItSystemChange change = groupByItSystemMap.get(itSystemId).stream().filter(c -> c.getEventType().equals(ItSystemChangeEventType.ITSYSTEM_MODIFY)).findAny().get();
					itSystemChanges.append("<dl style=\"margin-left: 0; padding-left: 4em;\">");
					itSystemChanges.append(div(dt("Navn") + dd(diff(change.getItSystemName(), itSystem.getName()))));
					itSystemChanges.append("</dl>");
				}
				body.append(messageSource.getMessage("html.email.it.system.change.message.update", new Object[] { itSystem.getName(), itSystemChanges.toString(), roleChanges.toString() }, locale));
			}
			
			String title = messageSource.getMessage("html.email.it.system.change.title", new Object[] { itSystem.getName() }, locale);//TODO change to old name if system was modified
			String message = messageSource.getMessage("html.email.it.system.change.message.body", new Object[] { body.toString() }, locale);
			
			try {
				emailService.sendMessage(emailAddress, title, message);
			}
			catch (Exception ex) {
				log.error("Exception occured while sending global email about ItSystem's SystemRole changes. Exception:" + ex.getMessage());
			}
			
			if (StringUtils.hasLength(itSystem.getNotificationEmail())) {
				try {
					emailService.sendMessage(itSystem.getNotificationEmail(), title, message);
				}
				catch (Exception ex) {
					log.error("Exception occured while sending direct email about ItSystem's SystemRole changes. Exception:" + ex.getMessage());
				}
			}
		}
		
		// and cleanup (note that we delete even if sending the email fails - otherwise we will fail every 15 minutes, and the lost email is not a big issue
		itSystemChangeService.delete(filtered);
	}
	
	private String div(String content) {
		return "<div>" + content + "</div>";
	}
	
	private String dt(String content) {
		return "<dt>" + content + "</dt>";
	}
	
	private String dd(String content) {
		return "<dd>" + content + "</dd>";
	}

	private String diff(String oldText, String newText) {
		DiffMatchPatch dmp = new DiffMatchPatch();
		LinkedList<Diff> diff = dmp.diff_main(oldText, newText);
		// Result: [(-1, "Hell"), (1, "G"), (0, "o"), (1, "odbye"), (0, " World.")]
		dmp.diff_cleanupSemantic(diff);
		// Result: [(-1, "Hello"), (1, "Goodbye"), (0, " World.")]
		String result = "";
		for (Diff d : diff) {
			switch (d.operation) {
				case DELETE:
					result += "<s style=\"background: rgb(255,0,0,0.3);\">" + d.text + "</s>";
					break;
				case INSERT:
					result += "<span style=\"background: rgb(0,255,0,0.3);\">" + d.text + "</span>";
					break;
				default:
					result += d.text;
					break;
			}
		}

		return result.replace("\r\n", "<br>");
	}
}
