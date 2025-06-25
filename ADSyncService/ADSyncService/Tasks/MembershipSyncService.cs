using ADSyncService.Email;
using ADSyncService.Persistance;
using ADSyncService.Util;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using static System.Windows.Forms.VisualStyles.VisualStyleElement.TrackBar;

namespace ADSyncService
{
    class MembershipSyncService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static EmailService emailService = EmailService.Instance;
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public void SynchronizeGroupMemberships(RoleCatalogueStub roleCatalogueStub, ADStub adStub, PersistenceService persistenceService, SyncData syncData)
        {
            bool ignoreUsersWithoutCpr = remoteConfigurationService.GetConfiguration().membershipSyncFeatureIgnoreUsersWithoutCpr;

            // retrieve map that contains settings for updating user attributes based on group membership.
            var attributeMap = GetAttributeMap();

            // if we have changes, perform update
            if (syncData.head > 0)
            {
                log.Info("Found potental AD group membership changes on " + syncData.assignments.Count + " group(s)");

                SyncGroups(adStub, persistenceService, syncData, localCacheEnabled: false, ignoreUsersWithoutCpr, attributeMap);

                // inform rolecatalogue that we are done sync'ing
                roleCatalogueStub.ResetHead(syncData.head, syncData.maxHead);
            }
        }

        public void SynchronizeAllGroupMemberships(RoleCatalogueStub roleCatalogueStub, ADStub adStub, PersistenceService persistenceService, SyncData syncData)
        {
            bool ignoreUsersWithoutCpr = remoteConfigurationService.GetConfiguration().membershipSyncFeatureIgnoreUsersWithoutCpr;

            // retrieve map that contains settings for updating user attributes based on group membership.
            var attributeMap = GetAttributeMap();

            log.Info("Found potental AD group membership changes on " + syncData.assignments.Count + " group(s)");

            SyncGroups(adStub, persistenceService, syncData, localCacheEnabled: true, ignoreUsersWithoutCpr, attributeMap);
        }

        private void SyncGroups(ADStub adStub, PersistenceService persistenceService, SyncData syncData, bool localCacheEnabled, bool ignoreUsersWithoutCpr, List<KeyValuePair<string, KeyValuePair<string, string>>> attributeMap)
        {
            IEnumerable<List<Assignment>> chunks = syncData.assignments.ChunkBy(4);

            foreach (var chunk in chunks)
            {
                Parallel.ForEach(chunk, (assignment) =>
                {
                    using (var cancelTokenSource = new CancellationTokenSource())
                    {
                        var cancelToken = cancelTokenSource.Token;

                        var task = Task.Run(() => UpdateGroup(adStub, persistenceService, ignoreUsersWithoutCpr, attributeMap, localCacheEnabled, assignment), cancelToken);

                        if (!task.Wait(TimeSpan.FromSeconds(60*30))) // 30min timeout
                        {
                            log.Warn("Timeout happended on " + assignment.groupName);

                            cancelTokenSource.Cancel();
                        }
                    }
                });
            }

            // remember to save changes to database
            if (localCacheEnabled)
            {
                log.Debug("Saving changes to SQLite");
                persistenceService.Save();
            }
        }

        private void UpdateGroup(ADStub adStub, PersistenceService persistenceService, bool ignoreUsersWithoutCpr, List<KeyValuePair<string, KeyValuePair<string, string>>> attributeMap, bool localCacheEnabled, Assignment assignment)
        {
            try
            {
                int added = 0;
                int removed = 0;

                if (localCacheEnabled)
                {
                    DateTime? lastUpdated = persistenceService.Get(assignment.groupName);
                    if (lastUpdated != null)
                    {
                        // found in cache
                        DateTime? whenChanged = adStub.GetGroupLastUpdated(assignment.groupName);
                        if (whenChanged != null && lastUpdated.Equals(whenChanged))
                        {
                            log.Debug("Skipping group "+ assignment.groupName + " has not changed since last time.");
                            return;
                        }
                    } else
                    {
                        // we never cached this group before
                    }
                }

                // all members are in lower-case, for easy comparison
                var adGroupMembers = adStub.GetGroupMembers(assignment.groupName, assignment.groupName);
                if (adGroupMembers == null)
                {
                    log.Warn("Unable to get members for: " + assignment.groupName);
                    return;
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
                if (localCacheEnabled)
                {
                    //Fetch new whenChanged value after we made changes and save it to cache
                    DateTime? whenChanged = adStub.GetGroupLastUpdated(assignment.groupName);
                    if (whenChanged != null)
                    {
                        persistenceService.Update(assignment.groupName, whenChanged.Value);
                    }
                }
            }
            catch (System.Exception ex)
            {
                log.Error("Failed to update group: " + assignment.groupName + ". Cause: " + ex.Message);
                emailService.EnqueueMail("Failed to update group: " + assignment.groupName, ex);
            }
        }

        private List<KeyValuePair<string, KeyValuePair<string, string>>> GetAttributeMap()
        {
            // result is KeyValuePair( groupName, KeyValuePair( attributeName, attributeValue ))
            var settingsMap = remoteConfigurationService.GetConfiguration().membershipSyncFeatureAttributeMap;
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
