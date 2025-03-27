package dk.digitalidentity.rc.dao.model.json;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ADConfigurationJSON {

	// create delete feature settings
	private boolean createDeleteFeatureEnabled;
	private String createDeleteFeatureOU;
	private boolean createDeleteFeatureCreateEnabled;
	private boolean createDeleteFeatureDeleteEnabled;

	// membership sync settings
	private String membershipSyncFeatureCprAttribute;
	private List<String> membershipSyncFeatureAttributeMap;
	private boolean membershipSyncFeatureEnabled;
	private boolean membershipSyncFeatureIgnoreUsersWithoutCpr;
	private boolean fullMembershipSyncFeatureEnabled;

	// backsync feature settings
	private boolean backSyncFeatureEnabled;
	private List<String> backSyncFeatureOUs;
	private boolean backSyncFeatureGroupsInGroupOnSync;
	private boolean backSyncFeatureCreateUserRoles;
	private String backSyncFeatureNameAttribute;

	// itSystem group feature settings
	private boolean itSystemGroupFeatureEnabled;
	private List<String> itSystemGroupFeatureSystemMap;

	// read only itSystem feature settings
	private boolean readonlyItSystemFeatureEnabled;
	private List<String> readonlyItSystemFeatureSystemMap;
	private String readonlyItSystemFeatureNameAttribute;

	// logUploader settings
	private boolean logUploaderEnabled;
	private String logUploaderFileShareUrl;
	private String logUploaderFileShareApiKey;

	// email settings
	private boolean sendErrorEmailFeatureEnabled;
	private String sendingUserEmail;
	private String recipientEmail;
	private String tenantId;
	private String clientId;
	private String clientSecret;

}
