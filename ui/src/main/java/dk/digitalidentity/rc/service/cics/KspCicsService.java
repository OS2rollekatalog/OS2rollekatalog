package dk.digitalidentity.rc.service.cics;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.AltAccountDao;
import dk.digitalidentity.rc.dao.DirtyKspCicsUserProfileDao;
import dk.digitalidentity.rc.dao.model.AltAccount;
import dk.digitalidentity.rc.dao.model.DirtyKspCicsUserProfile;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.KspCicsUnmatchedUser;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.KspCicsUnmatchedUserService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.cics.model.KspUser;
import dk.digitalidentity.rc.service.cics.model.KspUserProfile;
import dk.digitalidentity.rc.service.cics.model.KspUserProfilesResponse;
import dk.digitalidentity.rc.service.cics.model.KspUsersResponse;
import dk.digitalidentity.rc.service.cics.model.kmd.Envelope;
import dk.digitalidentity.rc.service.cics.model.kmd.ModifyWrapper;
import dk.digitalidentity.rc.service.cics.model.kmd.SearchEntry;
import dk.digitalidentity.rc.service.cics.model.kmd.SearchWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KspCicsService {
	private static final String SUCCESS_RESPONSE = "urn:oasis:names:tc:SPML:1:0#success";
	private static final String SOAP_ARG_LOSSHORTNAME = "{{LOS_SHORTNAME}}";
	private static final String SOAP_ARG_KSP_CICS_USER_ID = "{{KSP_CICS_USER_ID}}";
	private static final String SOAP_ARG_USER_PROFILE = "{{USER_PROFILE}}";
	
	// default wrappers around all requests
	private static final String SOAP_WRAPPER_BEGIN =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + 
    		"<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"" + 
    		"                  xmlns:kmd=\"http://www.kmd.dk/KMD.YH.KSPAabenSpml\">" + 
    		"  <soapenv:Header/>" + 
    		"  <soapenv:Body>";

	private static final String SOAP_WRAPPER_END =
			"  </soapenv:Body>\n" + 
    		"</soapenv:Envelope>";

	// wrappers around all search requests
	private static final String SOAP_SEARCH_REQUEST_BEGIN =
			"<kmd:SPMLSearchRequest>" + 
    		"  <kmd:request><![CDATA[" +
    		"    <spml:searchRequest xmlns:spml=\"urn:oasis:names:tc:SPML:1:0\" xmlns:dsml=\"urn:oasis:names:tc:DSML:2:0:core\">";

	private static final String SOAP_SEARCH_REQUEST_END =
			"    </spml:searchRequest>" +
			"  ]]></kmd:request>" + 
    		"</kmd:SPMLSearchRequest>";

	// wrappers around all modify requests
	private static final String SOAP_MODIFY_REQUEST_BEGIN =
			"<kmd:SPMLModifyRequest>" + 
    		"  <kmd:request><![CDATA[" +
    		"    <spml:modifyRequest xmlns:spml=\"urn:oasis:names:tc:SPML:1:0\" xmlns:dsml=\"urn:oasis:names:tc:DSML:2:0:core\">";

	private static final String SOAP_MODIFY_REQUEST_END =
			"    </spml:modifyRequest>" +
			"  ]]></kmd:request>" + 
    		"</kmd:SPMLModifyRequest>";
	
	// actual payload for modifying userProfile assignments
	private static final String SOAP_MODIFY_KSP_CICS_USER_START =
			"  <spml:identifier type=\"urn:oasis:names:tc:SPML:1:0#UserIDAndOrDomainName\">" + 
			"    <spml:id>" + SOAP_ARG_KSP_CICS_USER_ID+ "</spml:id>" + 
			"  </spml:identifier>" + 
			"  <spml:modifications>" + 
			"    <dsml:modification name=\"authorisations\" operation=\"replace\">";
	
	private static final String SOAP_MODIFY_KSP_CICS_USER_ROLE =
			"      <dsml:value>" + SOAP_ARG_USER_PROFILE + "</dsml:value>";

	private static final String SOAP_MODIFY_KSP_CICS_USER_STOP = 
			"    </dsml:modification>" + 
			"  </spml:modifications>";

	// actual payload for searching for users
	private static final String SOAP_SEARCH_USERS =
			"  <spml:searchBase type=\"urn:oasis:names:tc:SPML:1:0#UserIDAndOrDomainName\">" + 
    		"    <spml:id>" + SOAP_ARG_LOSSHORTNAME + "</spml:id>" + 
    		"  </spml:searchBase>";

	// actual payload for searching for userProfiles
	private static final String SOAP_SEARCH_USERPROFILES = 
    		"  <spml:searchBase type=\"urn:oasis:names:tc:SPML:1:0#DN\">" + 
    		"    <spml:id>" + SOAP_ARG_LOSSHORTNAME + "</spml:id>" + 
    		"  </spml:searchBase>";

	// actual payload for filtering userProfile search for specific userProfile
	private static final String SOAP_SEARCH_USERPROFILES_FILTER =
			"  <dsml:filter>" + 
			"    <dsml:equalityMatch name=\"userProfileName\">" + 
			"      <dsml:value>" + SOAP_ARG_USER_PROFILE + "</dsml:value>" + 
			"    </dsml:equalityMatch>" + 
			"  </dsml:filter>";
	
	@Qualifier("kspCicsRestTemplate")
	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private KspCicsUnmatchedUserService kspCicsUnmatchedUserService;

	@Autowired
	private AltAccountDao altAccountDao;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private UserService userService;
	
	@Autowired
	private DirtyKspCicsUserProfileDao dirtyKspCicsUserProfileDao;

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	public boolean initialized() {
		return (altAccountDao.countByAccountType(AltAccountType.KSPCICS) > 0);
	}

	@Transactional(rollbackFor = Exception.class)
	public void updateUserProfiles() {
		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.KSPCICS);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique KSP/CICS it-system (either 0 or > 1 was found!)");
			return;
		}
		ItSystem itSystem = itSystems.get(0);

		try {
			SecurityUtil.loginSystemAccount();

			KspUserProfilesResponse response = findUserProfiles(null);
			if (response != null && response.getUserProfiles() != null && response.getUserProfiles().size() > 0) {
				List<KspUserProfile> userProfiles = response.getUserProfiles();
				List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
				
				// if no systemRoles are present in the database for the KSP/CICS system, this is the first load,
				// and we should also handle role-assignments in that special case
				boolean initialLoad = (userRoles.size() == 0);
				List<AltAccount> altAccounts = new ArrayList<>();
				if (initialLoad) {
					altAccounts = altAccountDao.findByAccountType(AltAccountType.KSPCICS);
				}
				
				// create/update
				for (KspUserProfile userProfile : userProfiles) {
					UserRole existingUserRole = null;
	
					for (UserRole userRole : userRoles) {
						if (Objects.equals(userRole.getIdentifier(), userProfile.getId())) {
							existingUserRole = userRole;
							break;
						}
					}
	
					if (existingUserRole == null) {
						existingUserRole = new UserRole();
						existingUserRole.setDescription(userProfile.getDescription());
						existingUserRole.setIdentifier(userProfile.getId());
						existingUserRole.setItSystem(itSystem);
						existingUserRole.setName(userProfile.getName());
						existingUserRole.setSystemRoleAssignments(new ArrayList<>());
						existingUserRole = userRoleService.save(existingUserRole);
						
						log.info("Creating " + existingUserRole.getId() + " / " + existingUserRole.getName() + " / " + existingUserRole.getIdentifier());
					}
					else {
						boolean changes = false;
						
						if (!Objects.equals(existingUserRole.getDescription(), userProfile.getDescription())) {
							changes = true;
							existingUserRole.setDescription(userProfile.getDescription());
						}
						
						if (!Objects.equals(existingUserRole.getName(), userProfile.getName())) {
							changes = true;
							existingUserRole.setName(userProfile.getName());
						}
						
						if (changes) {
							log.info("Updating: " + existingUserRole.getId() + " / " + existingUserRole.getName() + " / " + existingUserRole.getIdentifier());
	
							userRoleService.save(existingUserRole);
						}
					}
					
					// if we are running in the initialLoad case, we also need to create the role-assignments
					if (initialLoad) {
	
						// Assign the UserRole
						for (String userId : userProfile.getUsers()) {
							List<AltAccount> matchingAltAccounts = altAccounts.stream().filter(a -> a.getAccountUserId().equals(userId)).collect(Collectors.toList());
							
							// someone needs to look at this
							if (matchingAltAccounts.size() > 1) {
								log.error("Found multiple AltAccounts with userId: " + userId);
							}
							
							for (AltAccount matchingAltAccount : matchingAltAccounts) {
								User user = matchingAltAccount.getUser();
								
								// bypass intercepter to avoid filling the dirty_ksp_cics_user_profiles table
								UserUserRoleAssignment assignment = new UserUserRoleAssignment();
								assignment.setUser(user);
								assignment.setUserRole(existingUserRole);
								assignment.setAssignedByName("KSP/CICS");
								assignment.setAssignedByUserId("KSP/CICS");
								assignment.setAssignedTimestamp(Date.from(LocalDateTime.of(1979, 5, 21, 8, 0).atZone(ZoneId.systemDefault()).toInstant()));
								user.getUserRoleAssignments().add(assignment);
								userService.save(user);
							}
						}
					}
				}
				
				// delete
				for (UserRole userRole : userRoles) {
					boolean found = false;
					
					for (KspUserProfile userProfile : userProfiles) {
						if (Objects.equals(userRole.getIdentifier(), userProfile.getId())) {
							found = true;
							break;
						}
					}
					
					if (!found) {
						log.info("Deleting " + userRole.getId() + " / " + userRole.getName() + " / " + userRole.getIdentifier());
						userRoleService.delete(userRole);
					}
				}
			}			
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void updateUsers() {
		long newAccounts = 0;
		long deletedAccounts = 0;
		long unmatchedAccounts = 0;

		List<AltAccount> altAccounts = altAccountDao.findByAccountType(AltAccountType.KSPCICS);
		KspUsersResponse response = findUsers();

		if (response != null && response.getUsers() != null && response.getUsers().size() > 0) {
			List<KspUser> kspUsers = response.getUsers();

			// find kspUsers to create/update
			for (KspUser kspUser : kspUsers) {
				boolean foundUser = false;
				
				boolean existingAltAccount = altAccounts.stream().anyMatch(a -> a.getAccountUserId().equals(kspUser.getUserId()));
				if (!existingAltAccount) {
					List<User> users = userService.findByCpr(kspUser.getCpr());
					if (users != null && users.size() > 0) {
						// sort, so we have some sort of consistent matching algorithm
						Optional<User> oUser = users.stream().sorted((u1, u2) -> u1.getUuid().compareTo(u2.getUuid())).findFirst();

						if (oUser.isPresent()) {
							User user = oUser.get();

							AltAccount altAccount = new AltAccount();
							altAccount.setAccountType(AltAccountType.KSPCICS);
							altAccount.setAccountUserId(kspUser.getUserId());
							altAccount.setUser(user);
	
							user.getAltAccounts().add(altAccount);
							userService.save(user);
							
							newAccounts++;
							
							foundUser = true;
						}
					}
				}
				else {
					foundUser = true;
				}

				if (foundUser) {
					kspCicsUnmatchedUserService.deleteByUserId(kspUser.getUserId());
				}
				else if (kspCicsUnmatchedUserService.getByUserId(kspUser.getUserId()) == null) {
					KspCicsUnmatchedUser unmatchedUser = new KspCicsUnmatchedUser();
					unmatchedUser.setCpr(kspUser.getCpr());
					unmatchedUser.setUserId(kspUser.getUserId());

					kspCicsUnmatchedUserService.save(unmatchedUser);
					unmatchedAccounts++;						
				}
			}
			
			for (AltAccount altAccount : altAccounts) {
				boolean existingKspUser = kspUsers.stream().anyMatch(k -> k.getUserId().equals(altAccount.getAccountUserId()));
				
				if (!existingKspUser) {
					User user = altAccount.getUser();
					
					user.getAltAccounts().remove(altAccount);
					userService.save(user);
					
					deletedAccounts++;
				}
			}
		}

		if (newAccounts > 0 || deletedAccounts > 0 || unmatchedAccounts > 0) {
			log.info("Created " + newAccounts + " KSP/CICS accounts and deleted " + deletedAccounts + " KSP/CICS accounts, and failed to match: " + unmatchedAccounts);
		}
	}
	
	@Transactional(rollbackFor = Exception.class)
	public void updateUserProfileAssignments() {
		List<DirtyKspCicsUserProfile> dirtyProfiles = dirtyKspCicsUserProfileDao.findAll();
		if (dirtyProfiles.size() == 0) {
			return;
		}
		
		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.KSPCICS);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique KSP/CICS it-system (either 0 or > 1 was found!)");
			return;
		}
		ItSystem itSystem = itSystems.get(0);

		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		List<DirtyKspCicsUserProfile> processed = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// all dirty profiles by userProfile ID (Set ensures uniqueness, useful for counting size later)
		Set<String> dirtyProfileIdentifiers = dirtyProfiles.stream()
				.map(d -> d.getIdentifier())
				.collect(Collectors.toSet());

		// all existing KSP/CICS accounts in the Role Catalogue (for filtering later)
		List<AltAccount> allAltAccounts = altAccountDao.findByAccountType(AltAccountType.KSPCICS);
		Set<String> allKnownKspCicsAccounts = allAltAccounts.stream()
				.map(a -> a.getAccountUserId())
				.collect(Collectors.toSet());

		// if there are more than 10 dirty userProfiles, we load ALL of them instead of loading them one
		// at a time, as this saves processing time overall
		List<KspUserProfile> userProfiles = new ArrayList<>();
		if (dirtyProfileIdentifiers.size() > 10) {
			KspUserProfilesResponse response = findUserProfiles(null);

			if (response != null && response.getUserProfiles() != null && response.getUserProfiles().size() > 0) {
				userProfiles = response.getUserProfiles();
			}
		}

		// we cannot modify role-assignments by modifying the userProfile, so first
		// we need to identify all "dirty" KspCics users from the list of dirty userProfiles
		Set<String> kspCicsUsersToUpdate = new HashSet<>();

		for (DirtyKspCicsUserProfile dirtyProfile : dirtyProfiles) {
			if (!seen.contains(dirtyProfile.getIdentifier())) {
				try {
					// ensure we only process each of these _once_
					seen.add(dirtyProfile.getIdentifier());
					
					Optional<UserRole> userRole = userRoles.stream().filter(u -> u.getIdentifier().equals(dirtyProfile.getIdentifier())).findFirst();
					if (userRole.isPresent()) {
						KspUserProfile kspUserProfile = getKspUserProfile(dirtyProfile.getIdentifier(), userProfiles);
						if (kspUserProfile != null) {
							// list of users with UserProfile assigned according to RoleCatalogue
							Set<String> altAccounts = userService.getUsersWithUserRole(userRole.get(), true).stream()
									.filter(u -> u.getUser().getAltAccounts().stream().anyMatch(a -> a.getAccountType().equals(AltAccountType.KSPCICS)))
									.map(u -> u.getUser().getAltAccounts().stream().filter(a -> a.getAccountType().equals(AltAccountType.KSPCICS)).findFirst())
									.filter(o -> o.isPresent())
									.map(o -> o.get())
									.map(a -> a.getAccountUserId())
									.collect(Collectors.toSet());
	
							// list of users with UserProfile assigned according to KSP/CICS
							Set<String> kspUsers = new HashSet<>(kspUserProfile.getUsers());
	
							// filter out all unknown KSP/CICS accounts (those not managed by the Role Catalogue)
							kspUsers.retainAll(allKnownKspCicsAccounts);
							
							if (!kspUsers.equals(altAccounts)) {
								Set<String> toRemove = new HashSet<>(kspUsers);
								toRemove.removeAll(altAccounts);
								
								Set<String> toAdd = new HashSet<>(altAccounts);
								toAdd.removeAll(kspUsers);

								// these are the dirty users, so they are added to the Set for later processing
								kspCicsUsersToUpdate.addAll(toRemove);
								kspCicsUsersToUpdate.addAll(toAdd);
							}
							
							processed.add(dirtyProfile);
						}
						else {
							// TODO: change to WARN after running in production for a while...
							// TODO: also we do not flag it as processed for now - might do that later, once we know that KMD is working
							log.error("Could not find dirty userProfile in KSP/CICS: " + dirtyProfile.getIdentifier());
						}
					}
				}
				catch (Exception ex) {
					log.error("Failed to process " + dirtyProfile.getIdentifier(), ex);

					// if it failed, we do not flag it as processed
					continue;
				}
			}
		}

		// cleanup queue
		dirtyKspCicsUserProfileDao.deleteAll(processed);
		
		// perform actual updates in KSP/CICS
		for (String dirtyKspCicsUser : kspCicsUsersToUpdate) {
			Optional<AltAccount> altAccount = allAltAccounts.stream().filter(a -> a.getAccountUserId().equals(dirtyKspCicsUser)).findFirst();

			if (altAccount.isPresent()) {
				User user = altAccount.get().getUser();
				
				Set<String> assignedUserProfiles = userService.getAllUserRoles(user, Collections.singletonList(itSystem)).stream()
						.map(u -> u.getIdentifier())
						.collect(Collectors.toSet());
				
				if (updateKspCicsUser(dirtyKspCicsUser, assignedUserProfiles)) {
					log.info("Updated KSP/CICS role assignments for " + dirtyKspCicsUser);
				}
				else {
					log.warn("Failed to update KSP/CICS role assignment for " + dirtyKspCicsUser);
				}
			}
		}
	}

	public void addUserRoleToQueue(UserRole userRole) {
		ItSystem itSystem = userRole.getItSystem();
		
		if (!itSystem.getSystemType().equals(ItSystemType.KSPCICS)) {
			return;
		}

		DirtyKspCicsUserProfile dirty = new DirtyKspCicsUserProfile();
		dirty.setIdentifier(userRole.getIdentifier());
		dirty.setTimestamp(new Date());

		dirtyKspCicsUserProfileDao.save(dirty);
	}

	private KspUserProfile getKspUserProfile(String identifier, List<KspUserProfile> userProfiles) {
		// check supplied cache first
		if (userProfiles != null && userProfiles.size() > 0) {
			Optional<KspUserProfile> oKspUserProfile = userProfiles.stream().filter(p -> p.getId().equals(identifier)).findFirst();

			if (oKspUserProfile.isPresent()) {
				return oKspUserProfile.get();
			}
		}
		
		KspUserProfilesResponse response = findUserProfiles(identifier);
		if (response != null && response.getUserProfiles() != null && response.getUserProfiles().size() > 0) {
			// we might get more than one, as we search for identifier without department, but the actual ID
			// contains both department and identifier as the "Id" field
			for (KspUserProfile profile : response.getUserProfiles()) {
				if (profile.getId().equals(identifier)) {
					return profile;
				}
			}
		}

		return null;
	}

	private boolean updateKspCicsUser(String kspCicsUserId, Set<String> userProfiles) {
    	HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "text/xml; charset=utf-8");
        headers.add("SOAPAction", "http://www.kmd.dk/KMD.YH.KSPAabenSpml/SPMLModifyRequest");		

        StringBuilder builder = new StringBuilder();
        builder.append(SOAP_WRAPPER_BEGIN);
        builder.append(SOAP_MODIFY_REQUEST_BEGIN);
        builder.append(SOAP_MODIFY_KSP_CICS_USER_START.replace(SOAP_ARG_KSP_CICS_USER_ID, kspCicsUserId));

        // add all userRoles that this user should be assigned
        for (String userProfile : userProfiles) {
            builder.append(SOAP_MODIFY_KSP_CICS_USER_ROLE.replace(SOAP_ARG_USER_PROFILE, escape(userProfile)));
        }

        builder.append(SOAP_MODIFY_KSP_CICS_USER_STOP);
        builder.append(SOAP_MODIFY_REQUEST_END);
        builder.append(SOAP_WRAPPER_END);
        
        String payload = builder.toString();
    	HttpEntity<String> request = new HttpEntity<String>(payload, headers);
    	ResponseEntity<String> response = null;
    	
    	// KMD has some issues, so we might have to try multiple times
    	int tries = 3;
    	do {
			response = restTemplate.postForEntity(configuration.getIntegrations().getKspcics().getUrl(), request, String.class);	
			if (response.getStatusCodeValue() != 200) {
				if (--tries >= 0) {
					log.warn("updateKspCicsUser - Got responseCode " + response.getStatusCodeValue() + " from service");
					
					try {
						Thread.sleep(5000);
					}
					catch (InterruptedException ex) {
						;
					}
				}
				else {
					log.error("updateKspCicsUser - Got responseCode " + response.getStatusCodeValue() + " from service. Request=" + request + " / Response=" + response.getBody());
					return false;
				}
			}
			else {
				break;
			}
    	} while (true);

		String responseBody = response.getBody();
		ModifyWrapper wrapper = null;

		try {
			XmlMapper xmlMapper = new XmlMapper();
			xmlMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

			Envelope envelope = xmlMapper.readValue(responseBody, Envelope.class);
			
			String modifyResponse = envelope.getBody().getSpmlModifyRequestResponse().getSpmlModifyRequestResult();
	
			wrapper = xmlMapper.readValue(modifyResponse, ModifyWrapper.class);
		}
		catch (Exception ex) {
			log.error("updateKspCicsUser - Failed to decode response: " + responseBody, ex);
			
			return false;
		}

		if (!SUCCESS_RESPONSE.equals(wrapper.getResult())) {
			if ("indeholder KSP/CICS-administrator funktioner".equals(wrapper.getErrorMessage())) {
				log.warn("updateKspCicsUser - Got a non-success response: " + wrapper.getResult() + ". Request=" + payload + " / Response=" + responseBody);
			}
			else {
				log.error("updateKspCicsUser - Got a non-success response: " + wrapper.getResult() + ". Request=" + payload + " / Response=" + responseBody);
			}
			
			return false;
		}

		return true;
	}

	// make the XML kings happy
	private String escape(String userProfile) {
		return userProfile.replace("&", "&amp;");
	}

	private KspUserProfilesResponse findUserProfiles(String optionalIdentifier) {
    	HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "text/xml; charset=utf-8");
        headers.add("SOAPAction", "http://www.kmd.dk/KMD.YH.KSPAabenSpml/SPMLSearchRequest");

        StringBuilder builder = new StringBuilder();
        builder.append(SOAP_WRAPPER_BEGIN);
        builder.append(SOAP_SEARCH_REQUEST_BEGIN);
        builder.append(SOAP_SEARCH_USERPROFILES.replace(SOAP_ARG_LOSSHORTNAME, configuration.getIntegrations().getKspcics().getLosid()));
        if (!StringUtils.isEmpty(optionalIdentifier)) {
        	// Identifiers may contain ; to separate actual Identifier and Department, so strip those
        	if (optionalIdentifier.contains(";")) {
        		int idx = optionalIdentifier.lastIndexOf(";");
        		optionalIdentifier = optionalIdentifier.substring(0, idx);
        	}

        	builder.append(SOAP_SEARCH_USERPROFILES_FILTER.replace(SOAP_ARG_USER_PROFILE, optionalIdentifier));
        }
        builder.append(SOAP_SEARCH_REQUEST_END);
        builder.append(SOAP_WRAPPER_END);

        String payload = builder.toString();
    	HttpEntity<String> request = new HttpEntity<String>(payload, headers);
		ResponseEntity<String> response;
		
    	// KMD has some issues, so we might have to try multiple times
    	int tries = 3;
    	do {
    		response = restTemplate.postForEntity(configuration.getIntegrations().getKspcics().getUrl(), request, String.class);
			if (response.getStatusCodeValue() != 200) {
				if (--tries >= 0) {
					log.warn("FindUserProfiles - Got responseCode " + response.getStatusCodeValue() + " from service");
					
					try {
						Thread.sleep(5000);
					}
					catch (InterruptedException ex) {
						;
					}
				}
				else {
					log.error("FindUserProfiles - Got responseCode " + response.getStatusCodeValue() + " from service. Request=" + request + " / Response=" + response.getBody());
					return null;
				}
			}
			else {
				break;
			}
    	} while (true);

		String responseBody = response.getBody();		
		SearchWrapper wrapper = null;

		try {
			XmlMapper xmlMapper = new XmlMapper();
			xmlMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

			Envelope envelope = xmlMapper.readValue(responseBody, Envelope.class);
			String searchResponse = envelope.getBody().getSpmlSearchRequestResponse().getSpmlSearchRequestResult();
	
			wrapper = xmlMapper.readValue(searchResponse, SearchWrapper.class);
		}
		catch (Exception ex) {
			log.error("FindUserProfiles - Failed to decode response: " + responseBody, ex);
			
			return null;
		}

		if (!SUCCESS_RESPONSE.equals(wrapper.getResult())) {
			log.error("FindUserProfiles - Got a non-success response: " + wrapper.getResult() + ". Request=" + payload + " / Response=" + responseBody);
			
			return null;
		}
		
		KspUserProfilesResponse kspUserProfilesResponse = new KspUserProfilesResponse();
		kspUserProfilesResponse.setUserProfiles(new ArrayList<>());
		if (wrapper.getSearchResultEntry() != null && wrapper.getSearchResultEntry().size() > 0) {
			for (SearchEntry entry : wrapper.getSearchResultEntry()) {
				KspUserProfile kspUserProfile = new KspUserProfile();
				kspUserProfile.setDescription(entry.getAttributes().getUserProfileDescription());
				kspUserProfile.setId(entry.getIdentifier().getId() + ";" + entry.getAttributes().getUserProfileDepartmentNumber());
				kspUserProfile.setName(entry.getAttributes().getUserProfileName() + "(" + entry.getAttributes().getUserProfileDepartmentNumber() + ")");
				kspUserProfile.setUsers(entry.getAttributes().getUserProfileUsers());
	
				kspUserProfilesResponse.getUserProfiles().add(kspUserProfile);
			}
		}

		return kspUserProfilesResponse;
	}

	private KspUsersResponse findUsers() {
    	HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "text/xml; charset=utf-8");
        headers.add("SOAPAction", "http://www.kmd.dk/KMD.YH.KSPAabenSpml/SPMLSearchRequest");

        StringBuilder builder = new StringBuilder();
        builder.append(SOAP_WRAPPER_BEGIN);
        builder.append(SOAP_SEARCH_REQUEST_BEGIN);
        builder.append(SOAP_SEARCH_USERS.replace(SOAP_ARG_LOSSHORTNAME, configuration.getIntegrations().getKspcics().getLosid()));
        builder.append(SOAP_SEARCH_REQUEST_END);
        builder.append(SOAP_WRAPPER_END);

        String payload = builder.toString();
    	HttpEntity<String> request = new HttpEntity<String>(payload, headers);
		ResponseEntity<String> response;
		
    	// KMD has some issues, so we might have to try multiple times
    	int tries = 3;
    	do {
    		response = restTemplate.postForEntity(configuration.getIntegrations().getKspcics().getUrl(), request, String.class);
			if (response.getStatusCodeValue() != 200) {
				if (--tries >= 0) {
					log.warn("FindUsers - Got responseCode " + response.getStatusCodeValue() + " from service");
					
					try {
						Thread.sleep(5000);
					}
					catch (InterruptedException ex) {
						;
					}
				}
				else {
					log.error("FindUsers - Got responseCode " + response.getStatusCodeValue() + " from service. Request=" + request + " / Response=" + response.getBody());
					return null;
				}
			}
			else {
				break;
			}
    	} while (true);

		String responseBody = response.getBody();		
		SearchWrapper wrapper = null;

		try {
			XmlMapper xmlMapper = new XmlMapper();
			xmlMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

			Envelope envelope = xmlMapper.readValue(responseBody, Envelope.class);
			String searchResponse = envelope.getBody().getSpmlSearchRequestResponse().getSpmlSearchRequestResult();
	
			wrapper = xmlMapper.readValue(searchResponse, SearchWrapper.class);
		}
		catch (Exception ex) {
			log.error("FindUsers - Failed to decode response: " + responseBody, ex);
			
			return null;
		}

		if (!SUCCESS_RESPONSE.equals(wrapper.getResult())) {
			log.error("FindUsers - Got a non-success response: " + wrapper.getResult() + ". Request=" + payload + " / Response=" + responseBody);
			
			return null;
		}
		
		KspUsersResponse kspUsersResponse = new KspUsersResponse();
		kspUsersResponse.setUsers(new ArrayList<>());
		for (SearchEntry entry : wrapper.getSearchResultEntry()) {
			KspUser kspUser = new KspUser();
			kspUser.setCpr(entry.getAttributes().getUserCpr());
			kspUser.setUserId(entry.getIdentifier().getId());

			kspUsersResponse.getUsers().add(kspUser);
		}

		return kspUsersResponse;
	}
}
