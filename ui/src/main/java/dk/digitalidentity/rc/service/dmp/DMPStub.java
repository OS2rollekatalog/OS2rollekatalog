package dk.digitalidentity.rc.service.dmp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.dmp.model.DMPApplication;
import dk.digitalidentity.rc.service.dmp.model.DMPCreateUser;
import dk.digitalidentity.rc.service.dmp.model.DMPCreateUserRequest;
import dk.digitalidentity.rc.service.dmp.model.DMPOverwriteRoleAssignmentsRequest;
import dk.digitalidentity.rc.service.dmp.model.DMPRole;
import dk.digitalidentity.rc.service.dmp.model.DMPRoleAssignment;
import dk.digitalidentity.rc.service.dmp.model.DMPSetRoleAssignmentsRequest;
import dk.digitalidentity.rc.service.dmp.model.DMPUser;
import dk.digitalidentity.rc.service.dmp.model.TokenResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableCaching
public class DMPStub {
	
	@Qualifier("defaultRestTemplate")
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private RoleCatalogueConfiguration config;
	
	@Autowired
	private DMPStub self;
	
	@Cacheable("dmp-token")
	public String fetchToken() {
		String url = config.getIntegrations().getDmp().getTokenUrl();
		String accessToken = null;
		
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/x-www-form-urlencoded");
		
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "client_credentials");
		body.add("client_id", config.getIntegrations().getDmp().getClientId());
		body.add("client_secret", config.getIntegrations().getDmp().getClientSecret());

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
		
		try {
			ResponseEntity<TokenResponse> response = restTemplate.exchange(url, HttpMethod.POST, request, TokenResponse.class);

			accessToken = response.getBody().getAccess_token();
		}
		catch (Exception ex) {
			log.error("Failed to fetch token from nemloginApi", ex);
		}
		
		return accessToken;
	}
	
	@CacheEvict(value = "dmp-token", allEntries = true)
	public void cleanUpToken() {
		;
	}
	
	public List<DMPApplication> getApplications() {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/applications";
		List<DMPApplication> applications = new ArrayList<>();

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		ResponseEntity<DMPApplication[]> response = restTemplate.exchange(url, HttpMethod.GET, request, DMPApplication[].class);

		applications = Arrays.asList(response.getBody());

		log.info("Found " + applications.size() + " applications in DMP");
		
		return applications;
	}
	
	public List<DMPRole> getRolesForApplication(DMPApplication application) {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/applications/" + application.getId() + "/roles";
		List<DMPRole> roles = new ArrayList<>();

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		ResponseEntity<DMPRole[]> response = restTemplate.exchange(url, HttpMethod.GET, request, DMPRole[].class);

		roles = Arrays.asList(response.getBody());

		log.info("Found " + roles.size() + " roles in DMP for application " + application.getName());
		
		return roles;
	}
	
	public List<DMPUser> getUsers() {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/users";
		List<DMPUser> users = new ArrayList<>();

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<DMPUser[]> response = restTemplate.exchange(url, HttpMethod.GET, request, DMPUser[].class);

			users = Arrays.asList(response.getBody());

			log.info("Found " + users.size() + " users in DMP");
		}
		catch (Exception ex) {
			log.error("Failed to fetch all users from dmpApi", ex);
		}
		
		return users;
	}
	
	// WARN: will return HTTP 200 with empty role array on userId that does not exist in DMP
	public List<DMPRole> getRolesForUser(String userId) {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/users/" + userId + "/role-assignments";
		List<DMPRole> roles = new ArrayList<>();

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<DMPRoleAssignment[]> response = restTemplate.exchange(url, HttpMethod.GET, request, DMPRoleAssignment[].class);

			List<DMPRoleAssignment> assignments = Arrays.asList(response.getBody());

			roles = assignments.stream().map(a -> a.getRole()).collect(Collectors.toList());

			log.debug("Found " + roles.size() + " roles in DMP for user " + userId);
		}
		catch (Exception ex) {
			log.error("Failed to fetch all roles for user " + userId + " from dmpApi", ex);
		}
		
		return roles;
	}
	
	// NOTE: Will return HTTP 400 on calls for user that does not exist in DMP with message "Entity was not found: User (xxx)"
	public boolean setRolesForUser(User user, DMPSetRoleAssignmentsRequest roles) {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/users/" + user.getUserId() + "/role-assignments";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<DMPSetRoleAssignmentsRequest> request = new HttpEntity<>(roles, headers);

		try {
			restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			log.info("Assigned roles for " + user.getUserId() + " (total " + roles.getUserRoleAssignments().size() + ") - details : " + roles.getUserRoleAssignments().stream().map(r -> r.getRoleId()).collect(Collectors.toSet()));
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException ex2) {
				String body = ex2.getResponseBodyAsString();
				int status = ex2.getRawStatusCode();

				if (status == 400 && body != null && body.contains("not found")) {
					DMPCreateUserRequest createUserRequest = DMPCreateUserRequest
							.builder()
							.users(new ArrayList<>())
							.build();
					
					createUserRequest.getUsers().add(DMPCreateUser
							.builder()
							.externalUserId(user.getUserId())
							.firstName(getFirstName(user))
							.lastName(getLastName(user))
							.email(getEmail(user))
							.build());
					
					if (createUser(createUserRequest)) {
						// try assigning again now that the user exists
						
						try {
							restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
	
							log.info("Assigned roles for newly created user " + user.getUserId() + " (total " + roles.getUserRoleAssignments().size() + ")");
							
							return true;
						}
						catch (Exception ex3) {
							log.error("Failed to set roles for user " + user.getUserId() + " using dmpApi", ex3);
							return false;							
						}
					}
					else {
						log.error("Cannot assign roles to " + user.getUserId() + " because the user does not exist in DMP, and creating the user failed");
						return false;
					}
				}
			}

			log.error("Failed to set roles for user " + user.getUserId() + " using dmpApi", ex);
			
			return false;
		}
		
		return true;
	}
	
	public boolean createUser(DMPCreateUserRequest createRequest) {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/users";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<DMPCreateUserRequest> request = new HttpEntity<>(createRequest, headers);

		try {
			restTemplate.exchange(url, HttpMethod.POST, request, String.class);

			log.info("Created user " + createRequest.getUsers().get(0).getExternalUserId());
		}
		catch (Exception ex) {
			log.error("Failed to create user " + createRequest.getUsers().get(0).getExternalUserId() + " using dmpApi", ex);
			
			return false;
		}
		
		return true;
	}
	
	public boolean deleteUser(String userId) {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/users/" + userId;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		try {
			restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);

			log.info("Deleted user " + userId);
		}
		catch (Exception ex) {
			log.error("Failed to delete user " + userId + " using dmpApi", ex);
			
			return false;
		}
		
		return true;
	}
	
	public boolean overwriteAllRoleAssignments(DMPOverwriteRoleAssignmentsRequest body) {
		String url = config.getIntegrations().getDmp().getServiceUrl() + "/role-assignments";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<DMPOverwriteRoleAssignmentsRequest> request = new HttpEntity<>(body, headers);

		try {
			restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			log.info("Performed full overwrite of all role assignments");
		}
		catch (Exception ex) {
			log.error("Failed to perform full overwrite of all role assignments", ex);
			
			return false;
		}
		
		return true;
	}
	
	private String getEmail(User user) {
		if (StringUtils.hasLength(user.getEmail())) {
			return user.getEmail();
		}
		
		return config.getIntegrations().getDmp().getDummyEmail().replace("{userId}", user.getUserId());
	}

	private String getLastName(User user) {
		if (!StringUtils.hasLength(user.getName())) {
			return "Ukendtsen";
		}
		
		String name = user.getName().trim();
		if (!name.contains(" ")) {
			return "Ukendtsen";
		}
		
		String[] parts = name.split(" ");
		
		return parts[parts.length - 1];
	}

	private String getFirstName(User user) {
		if (!StringUtils.hasLength(user.getName())) {
			return "Ukendt";
		}
		
		String name = user.getName().trim();
		if (!name.contains(" ")) {
			return name;
		}

		int cutPoint = name.lastIndexOf(" ");
		
		return name.substring(0, cutPoint);
	}
}
