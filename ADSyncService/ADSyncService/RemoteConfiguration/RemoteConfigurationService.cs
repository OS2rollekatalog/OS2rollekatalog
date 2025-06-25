using System;
using System.Collections.Generic;
using System.Collections.Specialized;

namespace ADSyncService
{
    class RemoteConfigurationService
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool featureEnabled = Properties.Settings.Default.RemoteConfiguration_Enabled;

        private static readonly Lazy<RemoteConfigurationService> lazy = new Lazy<RemoteConfigurationService>(() => new RemoteConfigurationService());
        public static RemoteConfigurationService Instance { get { return lazy.Value; } }

        private bool initialized = false;
        private RemoteConfiguration remoteConfiguration = null;
        private RemoteConfiguration localConfiguration = null;

        private RemoteConfigurationService() {}

        private void init()
        {
            SetLocalConfiguration();
            initialized = true;
        }

        public RemoteConfiguration GetConfiguration()
        {
            if (!initialized) { init(); }

            if (featureEnabled)
            {
                if (remoteConfiguration == null)
                {
                    throw new Exception("ADSyncService depends on remote configuration, but it is null");
                }
                else
                {
                    return remoteConfiguration;
                }
            }

            return localConfiguration;
        }

        public void FetchConfiguration(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            if (!initialized) { init(); }

            if (!featureEnabled) { return; }
            RemoteConfiguration configuration = roleCatalogueStub.GetConfiguration();
            if (configuration == null)
            {
                return;
            }
            else
            {
                bool validated = ValidateConfiguration(configuration, roleCatalogueStub, adStub);
                if (validated)
                {
                    remoteConfiguration = configuration;
                }
            }
        }

        private bool ValidateConfiguration(RemoteConfiguration configuration, RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            bool valid = true;
            string errorMsg = "Vi fandt følgende fejl i opsætningen:\n";

            // validate that ous and groups exists
            if (configuration.createDeleteFeatureEnabled && configuration.createDeleteFeatureOU == null)
            {
                errorMsg += "createDeleteFeatureEnabled er slået til men createDeleteFeatureOU er ikke konfigureret\n";
                valid = false;
            }
            else if (configuration.createDeleteFeatureEnabled && configuration.createDeleteFeatureOU != null)
            {
                if (!adStub.EntityExistsInAD(configuration.createDeleteFeatureOU))
                {
                    errorMsg += "Konfigureret createDeleteFeatureOU eksisterer ikke i AD\n";
                    valid = false;
                }
            }

            if (configuration.backSyncFeatureOUs != null)
            {
                foreach (var ouDN in configuration.backSyncFeatureOUs)
                {
                    string dnWithoutWildcard = ouDN.Split(';')[1].Replace("*", "");
                    if (!adStub.EntityExistsInAD(dnWithoutWildcard))
                    {
                        errorMsg += "Konfigureret backSyncFeatureOU: " + ouDN + " eksisterer ikke i AD\n";
                        valid = false;
                    }
                }
            }

            if (configuration.itSystemGroupFeatureSystemMap != null)
            {
                foreach (var groupDN in configuration.itSystemGroupFeatureSystemMap)
                {
                    if (!adStub.EntityExistsInAD(groupDN.Split(';')[1]))
                    {
                        errorMsg += "Konfigureret itSystemGroupFeatureSystemMap gruppe: " + groupDN + " eksisterer ikke i AD\n";
                        valid = false;
                    }
                }
            }

            if (configuration.itSystemGroupFeatureRoleMap != null)
            {
                foreach (var groupDN in configuration.itSystemGroupFeatureRoleMap)
                {
                    if (!adStub.EntityExistsInAD(groupDN.Split(';')[1]))
                    {
                        errorMsg += "Konfigureret itSystemGroupFeatureRoleMap gruppe: " + groupDN + " eksisterer ikke i AD\n";
                        valid = false;
                    }
                }
            }

            if (configuration.readonlyItSystemFeatureSystemMap != null)
            {
                foreach (var groupDN in configuration.readonlyItSystemFeatureSystemMap)
                {
                    if (!adStub.EntityExistsInAD(groupDN.Split(';')[1]))
                    {
                        errorMsg += "Konfigureret readonlyItSystemFeatureSystemMap gruppe: " + groupDN + " eksisterer ikke i AD\n";
                        valid = false;
                    }
                }
            }

            if (!valid)
            {
                roleCatalogueStub.SendConfigurationError(errorMsg);
            } 
            else
            {
                // Clear the error if configuration is valid
                roleCatalogueStub.SendConfigurationError("");
            }

            return valid;
        }

        public RemoteConfiguration GetLocalConfiguration()
        {
            if (!initialized) { init(); }

            return localConfiguration;
        }

        private void SetLocalConfiguration()
        {
            RemoteConfiguration configuration = new RemoteConfiguration();
            configuration.createDeleteFeatureCreateEnabled = Properties.Settings.Default.CreateDeleteFeature_Enabled;
            configuration.createDeleteFeatureOU = Properties.Settings.Default.CreateDeleteFeature_OU;
            configuration.createDeleteFeatureCreateEnabled = Properties.Settings.Default.CreateDeleteFeature_CreateEnabled;
            configuration.createDeleteFeatureDeleteEnabled = Properties.Settings.Default.CreateDeleteFeature_DeleteEnabled;

            configuration.membershipSyncFeatureCprAttribute = Properties.Settings.Default.MembershipSyncFeature_CprAttribute;
            configuration.membershipSyncFeatureAttributeMap = ConvertToList(Properties.Settings.Default.MembershipSyncFeature_AttributeMap);
            configuration.membershipSyncFeatureEnabled = Properties.Settings.Default.MembershipSyncFeature_Enabled;
            configuration.membershipSyncFeatureIgnoreUsersWithoutCpr = Properties.Settings.Default.MembershipSyncFeature_IgnoreUsersWithoutCpr;

            configuration.fullMembershipSyncFeatureEnabled = Properties.Settings.Default.FullMembershipSyncFeature_Enabled;

            configuration.backSyncFeatureEnabled = Properties.Settings.Default.BackSyncFeature_Enabled;
            configuration.backSyncFeatureOUs = ConvertToList(Properties.Settings.Default.BackSyncFeature_OUs);
            configuration.backSyncFeatureGroupsInGroupOnSync = Properties.Settings.Default.BackSyncFeature_GroupsInGroupOnSync;
            configuration.backSyncFeatureCreateUserRoles = Properties.Settings.Default.BackSyncFeature_CreateUserRoles;
            configuration.backSyncFeatureNameAttribute = Properties.Settings.Default.BackSyncFeature_NameAttribute;

            configuration.itSystemGroupFeatureEnabled = Properties.Settings.Default.ItSystemGroupFeature_Enabled;
            configuration.itSystemGroupFeatureSystemMap = ConvertToList(Properties.Settings.Default.ItSystemGroupFeature_SystemMap);
            configuration.itSystemGroupFeatureRoleMap = ConvertToList(Properties.Settings.Default.ItSystemGroupFeature_RoleMap);

            configuration.readonlyItSystemFeatureEnabled = Properties.Settings.Default.ReadonlyItSystemFeature_Enabled;
            configuration.readonlyItSystemFeatureSystemMap = ConvertToList(Properties.Settings.Default.ReadonlyItSystemFeature_SystemMap);
            configuration.readonlyItSystemFeatureNameAttribute = Properties.Settings.Default.ReadonlyItSystemFeature_NameAttribute;

            configuration.logUploaderEnabled = Properties.Settings.Default.LogUploaderEnabled;
            configuration.logUploaderFileShareUrl = Properties.Settings.Default.LogUploaderFileShareUrl;
            configuration.logUploaderFileShareApiKey = Properties.Settings.Default.LogUploaderFileShareApiKey;

            configuration.sendErrorEmailFeatureEnabled = Properties.Settings.Default.SendErrorEmailFeature_Enabled;
            configuration.sendingUserEmail = Properties.Settings.Default.User;
            configuration.recipientEmail = Properties.Settings.Default.RecipientEmail;
            configuration.tenantId = Properties.Settings.Default.TenantId;
            configuration.clientId = Properties.Settings.Default.ClientId;
            configuration.clientSecret = Properties.Settings.Default.ClientSecret;

            configuration.includeNotesInDescription = Properties.Settings.Default.IncludeNotesInDescription;

            localConfiguration = configuration;
        }

        public List<string> ConvertToList(StringCollection stringCollection)
        {
            var list = new List<string>();
            if (stringCollection != null)
            {
                foreach (var item in stringCollection)
                {
                    list.Add(item);
                }
            }
            return list;
        }
    }
}
