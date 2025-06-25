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
            if (systemMap != null)
            {
                foreach (string mapRaw in systemMap)
                {
                    string[] tokens = SplitMap(mapRaw);
                    if (tokens.Length != 2)
                    {
                        log.Warn("Invalid entry in itSystemGroupFeatureSystemMap update: " + mapRaw);
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

                    HandleGroupMembers(adStub, groupDn, users);
                }
            }

            List<string> roleMap = remoteConfigurationService.GetConfiguration().itSystemGroupFeatureRoleMap;
            if (roleMap != null)
            {
                foreach (string mapRaw in roleMap)
                {
                    string[] tokens = SplitMap(mapRaw);
                    if (tokens.Length != 2)
                    {
                        log.Warn("Invalid entry in itSystemGroupFeatureRoleMap update: " + mapRaw);
                        continue;
                    }

                    string roleId = tokens[0];
                    string groupDn = tokens[1];

                    List<string> users = roleCatalogueStub.GetUsersWithRole(roleId);
                    if (users == null)
                    {
                        log.Warn("Unable to get users with role from role catalogue: " + roleId);
                        continue;
                    }

                    HandleGroupMembers(adStub, groupDn, users);
                }
            }
        }

        private static void HandleGroupMembers(ADStub adStub, string groupDn, List<string> users)
        {
            List<string> members = adStub.GetGroupMembers(groupDn, groupDn);
            if (members == null)
            {
                log.Warn("Unable to get members from group: " + groupDn);
                return;
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

        private static string[] SplitMap(string mapRaw)
        {
            var map = mapRaw.Replace("&amp;", "&");
            string[] tokens = map.Split(';');

            return tokens;
        }
    }
}
