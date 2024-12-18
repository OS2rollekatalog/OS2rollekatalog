using System.Collections.Generic;

namespace ADSyncService
{
    public class RemoteConfiguration
    {
        public bool createDeleteFeatureEnabled { get; set; }
        public string createDeleteFeatureOU { get; set; }
        public bool createDeleteFeatureCreateEnabled { get; set; }
        public bool createDeleteFeatureDeleteEnabled { get; set; }

        public string membershipSyncFeatureCprAttribute { get; set; }
        public List<string> membershipSyncFeatureAttributeMap { get; set; }
        public bool membershipSyncFeatureEnabled { get; set; }
        public bool membershipSyncFeatureIgnoreUsersWithoutCpr { get; set; }

        public bool backSyncFeatureEnabled { get; set; }
        public List<string> backSyncFeatureOUs { get; set; }
        public bool backSyncFeatureGroupsInGroupOnSync { get; set; }
        public bool backSyncFeatureCreateUserRoles { get; set; }
        public string backSyncFeatureNameAttribute { get; set; }

        public bool itSystemGroupFeatureEnabled { get; set; }
        public List<string> itSystemGroupFeatureSystemMap { get; set; }

        public bool readonlyItSystemFeatureEnabled { get; set; }
        public List<string> readonlyItSystemFeatureSystemMap { get; set; }
        public string readonlyItSystemFeatureNameAttribute { get; set; }

        public bool logUploaderEnabled { get; set; }
        public string logUploaderFileShareUrl { get; set; }
        public string logUploaderFileShareApiKey { get; set; }

        public bool sendErrorEmailFeatureEnabled { get; set; }
        public string sendingUserEmail { get; set; }
        public string recipientEmail { get; set; }
        public string tenantId { get; set; }
        public string clientId { get; set; }
        public string clientSecret { get; set; }
    }
}
