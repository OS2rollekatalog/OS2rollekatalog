using System.Collections.Specialized;
using System.Collections.Generic;

namespace ADSyncService
{
    class ItSystemGroupService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public void PerformUpdate(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            List<string> systemMap = remoteConfigurationService.GetConfiguration().itSystemGroupFeatureSystemMap;
            foreach (string mapRaw in systemMap)
            {
                var map = mapRaw.Replace("&amp;", "&");
                string[] tokens = map.Split(';');
                if (tokens.Length != 2)
                {
                    log.Warn("Invalid entry in ItSystemGroup update: " + map);
                    continue;
                }

                string itSystemId = tokens[0];
                string groupDn = tokens[1];

                List<string> users = roleCatalogueStub.GetUsersInItSystem(itSystemId);
                if (users == null)
                {
                    log.Warn("Unable to get users from role catalogue: " + itSystemId);
                    continue;
                }

                List<string> members = adStub.GetGroupMembers(groupDn);
                if (members == null)
                {
                    log.Warn("Unable to get members from group: " + groupDn);
                    continue;
                }

                // to add
                foreach (string user in users)
                {
                    bool found = false;

                    foreach (string member in members)
                    {
                        if (string.Equals(user, member, System.StringComparison.OrdinalIgnoreCase))
                        {
                            found = true;
                            break;
                        }
                    }

                    if (!found)
                    {
                        adStub.AddMember(groupDn, user);
                    }
                }

                // to remove
                foreach (string member in members)
                {
                    bool found = false;

                    foreach (string user in users)
                    {
                        if (string.Equals(user, member, System.StringComparison.OrdinalIgnoreCase))
                        {
                            found = true;
                            break;
                        }
                    }

                    if (!found)
                    {
                        adStub.RemoveMember(groupDn, member);
                    }
                }
            }
        }
    }
}
