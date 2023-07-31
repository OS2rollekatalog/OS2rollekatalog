using System.Collections.Generic;
using System.Linq;

namespace ADSyncService
{
    class MembershipSyncService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool ignoreUsersWithoutCpr = Properties.Settings.Default.MembershipSyncFeature_IgnoreUsersWithoutCpr;

        public static void SynchronizeGroupMemberships(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            // retrieve map that contains settings for updating user attributes based on group membership.
            var attributeMap = GetAttributeMap();

            // retrieve any changes from role catalogue
            var syncData = roleCatalogueStub.GetSyncData();

            // if we have changes, perform update
            if (syncData.head > 0)
            {
                log.Info("Found potental AD group membership changes on " + syncData.assignments.Count + " group(s)");

                foreach (var assignment in syncData.assignments)
                {
                    try
                    {
                        int added = 0;
                        int removed = 0;

                        // all members are in lower-case, for easy comparison
                        var adGroupMembers = adStub.GetGroupMembers(assignment.groupName);
                        if (adGroupMembers == null)
                        {
                            log.Warn("Unable to get members for: " + assignment.groupName);
                            continue;
                        }

                        log.Info("Found " + adGroupMembers.Count + " existing members of " + assignment.groupName + " with expected end-result being " + assignment.samaccountNames.Count + " members");

                        // log all potential members and all existing members
                        if (log.IsDebugEnabled)
                        {
                            log.Debug("All AD group members: " + string.Join(",", adGroupMembers));
                            log.Debug("All OS2rollekatalog group members: " + string.Join(",", assignment.samaccountNames));
                        }

                        foreach (var userId in assignment.samaccountNames)
                        {
                            if (!adGroupMembers.Contains(userId))
                            {
                                adStub.AddMember(assignment.groupName, userId);
                                added++;

                                // update user's ad attributes with values specified in configuration
                                foreach (var attributeSetting in attributeMap.Where(am => am.Key == assignment.groupName.ToLower()))
                                {
                                    adStub.UpdateAttribute(userId, attributeSetting.Value.Key, attributeSetting.Value.Value);
                                }                                    
                            }
                        }

                        foreach (var userId in adGroupMembers)
                        {
                            if (!assignment.samaccountNames.Contains(userId))
                            {
                                if (!ignoreUsersWithoutCpr || adStub.HasCpr(userId))
                                {
                                    adStub.RemoveMember(assignment.groupName, userId);
                                    removed++;

                                    // clear user's ad attributes if any was specified in configuration
                                    foreach (var attributeSetting in attributeMap.Where(am => am.Key == assignment.groupName.ToLower()))
                                    {
                                        adStub.ClearAttribute(userId, attributeSetting.Value.Key);
                                    }
                                }
                                else
                                {
                                    log.Info("Did not remove " + userId + " from " + assignment.groupName + " because it was a non-cpr user");
                                }
                            }
                        }

                        log.Info("Added " + added + " new group memberships, and removed " + removed + " group memberships from group: " + assignment.groupName);
                    }
                    catch (System.Exception ex)
                    {
                        log.Error("Failed to update group: " + assignment.groupName + ". Cause: " + ex.Message);
                    }
                }

                // inform rolecatalogue that we are done sync'ing
                roleCatalogueStub.ResetHead(syncData.head, syncData.maxHead);
            }
        }

        private static List<KeyValuePair<string, KeyValuePair<string, string>>> GetAttributeMap()
        {
            // result is KeyValuePair( groupName, KeyValuePair( attributeName, attributeValue ))
            var settingsMap = Properties.Settings.Default.MembershipSyncFeature_AttributeMap;
            var result = new List<KeyValuePair<string, KeyValuePair<string, string>>>();
            if (settingsMap != null)
            {
                foreach (var mapping in settingsMap)
                {
                    var values = mapping.Split(';');
                    if (values.Count() != 3)
                    {
                        log.Warn("Invalid attributemap value: " + mapping);
                        continue;
                    }
                    result.Add(new KeyValuePair<string,KeyValuePair<string,string>>(values[0].ToLower(), new KeyValuePair<string, string>(values[1], values[2])));
                }
            }
            return result;
        }

    }
}
