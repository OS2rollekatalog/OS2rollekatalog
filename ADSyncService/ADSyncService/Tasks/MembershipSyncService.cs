using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ADSyncService
{
    class MembershipSyncService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool ignoreUsersWithoutCpr = Properties.Settings.Default.MembershipSyncFeature_IgnoreUsersWithoutCpr;

        public static void SynchronizeGroupMemberships(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
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

                        foreach (var userId in assignment.samaccountNames)
                        {
                            if (!adGroupMembers.Contains(userId))
                            {
                                adStub.AddMember(assignment.groupName, userId);
                                added++;
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
                roleCatalogueStub.ResetHead(syncData.head);
            }
        }
    }
}
