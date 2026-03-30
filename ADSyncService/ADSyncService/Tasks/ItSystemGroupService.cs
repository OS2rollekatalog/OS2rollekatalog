using System;
using System.Collections.Generic;
using System.Collections.Specialized;

namespace ADSyncService
{
    class ItSystemGroupService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public void PerformUpdate(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            bool doNotRegisterDisabledUsers = remoteConfigurationService.GetConfiguration().itSystemGroupFeatureDoNotRegisterDisabledUsers;

            // Collect users per AD group from both systems and roles
            var groupUserMap = new Dictionary<string, HashSet<string>>(StringComparer.OrdinalIgnoreCase);

            // Process system mappings
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

                    AddUsersToGroup(groupUserMap, groupDn, users);
                }
            }

            // Process role mappings
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

                    AddUsersToGroup(groupUserMap, groupDn, users);
                }
            }

            // Handle each AD group once with the combined user list
            foreach (var entry in groupUserMap)
            {
                HandleGroupMembers(adStub, entry.Key, new List<string>(entry.Value), doNotRegisterDisabledUsers);
            }
        }

        private static void AddUsersToGroup(Dictionary<string, HashSet<string>> groupUserMap, string groupDn, List<string> users)
        {
            if (!groupUserMap.TryGetValue(groupDn, out HashSet<string> userSet))
            {
                userSet = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                groupUserMap[groupDn] = userSet;
            }

            foreach (string user in users)
            {
                userSet.Add(user);
            }
        }

        private static void HandleGroupMembers(ADStub adStub, string groupDn, List<string> users, bool doNotRegisterDisabledUsers)
        {
            var members = adStub.GetGroupMembers(groupDn, groupDn);
            if (members == null)
            {
                log.Warn("Unable to get members from group: " + groupDn);
                return;
            }

            // Create HashSet for better performance when checking membership
            var memberSet = new HashSet<string>(members, StringComparer.OrdinalIgnoreCase);
            var userSet = new HashSet<string>(users, StringComparer.OrdinalIgnoreCase);

            // Add users to group
            foreach (string user in users)
            {
                if (!memberSet.Contains(user))
                {
                    // Check if user is active before adding if doNotRegisterDisabledUsers is true
                    if (doNotRegisterDisabledUsers)
                    {
                        bool isUserActive = adStub.IsUserActive(user);
                        if (!isUserActive)
                        {
                            log.Debug($"ITSystenGroupService.HandleGroupMembers: Skipping disabled user: {user}");
                            continue;
                        }
                    }

                    adStub.AddMember(groupDn, user);
                }
            }

            // Remove members not in user list
            foreach (string member in members)
            {
                if (!userSet.Contains(member))
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
