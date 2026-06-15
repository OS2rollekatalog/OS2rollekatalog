using ADSyncService.Email;
using ADSyncService.Persistance;
using ADSyncService.Util;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;
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
            bool doNotRegisterDisabledUsers = remoteConfigurationService.GetConfiguration().membershipSyncFeatureDoNotRegisterDisabledUsers;

            // retrieve map that contains settings for updating user attributes based on group membership.
            var attributeMap = GetAttributeMap();

            // retrieve map that contains filters for group memberships.
            var filterMap = GetFilterMap();

            // if we have changes, perform update
            if (syncData.head > 0)
            {
                log.Info("Found potental AD group membership changes on " + syncData.assignments.Count + " group(s)");

                SyncGroups(adStub, persistenceService, syncData, localCacheEnabled: false, ignoreUsersWithoutCpr, attributeMap, filterMap, doNotRegisterDisabledUsers);

                // inform rolecatalogue that we are done sync'ing
                roleCatalogueStub.ResetHead(syncData.head, syncData.maxHead);
            }
        }

        public void SynchronizeAllGroupMemberships(RoleCatalogueStub roleCatalogueStub, ADStub adStub, PersistenceService persistenceService, SyncData syncData)
        {
            bool ignoreUsersWithoutCpr = remoteConfigurationService.GetConfiguration().membershipSyncFeatureIgnoreUsersWithoutCpr;
            bool doNotRegisterDisabledUsers = remoteConfigurationService.GetConfiguration().membershipSyncFeatureDoNotRegisterDisabledUsers;

            // retrieve map that contains settings for updating user attributes based on group membership.
            var attributeMap = GetAttributeMap();

            // retrieve map that contains filters for group memberships.
            var filterMap = GetFilterMap();

            log.Info("Found potental AD group membership changes on " + syncData.assignments.Count + " group(s)");

            SyncGroups(adStub, persistenceService, syncData, localCacheEnabled: true, ignoreUsersWithoutCpr, attributeMap, filterMap, doNotRegisterDisabledUsers);
        }

        private void SyncGroups(ADStub adStub, PersistenceService persistenceService, SyncData syncData, bool localCacheEnabled, bool ignoreUsersWithoutCpr, List<KeyValuePair<string, KeyValuePair<string, string>>> attributeMap, List<KeyValuePair<string, KeyValuePair<string, string>>> filterMap, bool doNotRegisterDisabledUsers)
        {
            var userCache = new ConcurrentDictionary<string, UserCache>(StringComparer.OrdinalIgnoreCase);

            // Collect all attribute names needed for filter checks upfront so each user is loaded with everything in one AD call
            var attributeNamesToLoad = filterMap.Select(f => f.Value.Key).Distinct().ToList();

            IEnumerable<List<Assignment>> chunks = syncData.assignments.ChunkBy(4);

            foreach (var chunk in chunks)
            {
                Parallel.ForEach(chunk, (assignment) =>
                {
                    using (var cancelTokenSource = new CancellationTokenSource())
                    {
                        var cancelToken = cancelTokenSource.Token;

                        var task = Task.Run(() => UpdateGroup(adStub, persistenceService, ignoreUsersWithoutCpr, attributeMap, localCacheEnabled, assignment, filterMap, doNotRegisterDisabledUsers, userCache, attributeNamesToLoad), cancelToken);

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

        private void UpdateGroup(ADStub adStub, PersistenceService persistenceService, bool ignoreUsersWithoutCpr, List<KeyValuePair<string, KeyValuePair<string, string>>> attributeMap, bool localCacheEnabled, Assignment assignment, List<KeyValuePair<string, KeyValuePair<string, string>>> filterMap, bool doNotRegisterDisabledUsers, ConcurrentDictionary<string, UserCache> userCache, List<string> attributeNamesToLoad)
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
                        if (ShouldIncludeUser(userId, assignment.groupName, filterMap, adStub, userCache, attributeNamesToLoad))
                        {
                            // Check if user is active before adding if doNotRegisterDisabledUsers is true
                            if (doNotRegisterDisabledUsers)
                            {
                                bool isUserActive = adStub.IsUserActive(userId, userCache, attributeNamesToLoad);
                                if (!isUserActive)
                                {
                                    log.Info($"Skipping disabled user: {userId}");
                                    continue;
                                }
                            }

                            adStub.AddMember(assignment.groupName, userId);
                            added++;

                            // update user's ad attributes with values specified in configuration
                            foreach (var attributeSetting in attributeMap.Where(am => am.Key == assignment.groupName.ToLower()))
                            {
                                adStub.UpdateAttribute(userId, attributeSetting.Value.Key, attributeSetting.Value.Value);
                            }
                        }
                        else
                        {
                            log.Debug($"User {userId} filtered out from group {assignment.groupName} due to filter rules");
                        }
                    }
                }

                foreach (var userId in adGroupMembers)
                {
                    bool shouldBeInGroup = assignment.samaccountNames.Contains(userId) &&
                          ShouldIncludeUser(userId, assignment.groupName, filterMap, adStub, userCache, attributeNamesToLoad);
                    if (!shouldBeInGroup)
                    {
                        if (!ignoreUsersWithoutCpr || adStub.HasCpr(userId, userCache, attributeNamesToLoad))
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

        private List<KeyValuePair<string, KeyValuePair<string, string>>> GetFilterMap()
        {
            // result is KeyValuePair( groupName, KeyValuePair( attributeName, filterValue ))
            var settingsMap = remoteConfigurationService.GetConfiguration().membershipSyncFeatureFilterMap;
            var result = new List<KeyValuePair<string, KeyValuePair<string, string>>>();
            if (settingsMap != null)
            {
                foreach (var mapping in settingsMap)
                {
                    var values = mapping.Split(';');
                    if (values.Count() != 3)
                    {
                        log.Warn("Invalid Filtermap value: " + mapping);
                        continue;
                    }
                    result.Add(new KeyValuePair<string, KeyValuePair<string, string>>(values[0].ToLower(), new KeyValuePair<string, string>(values[1], values[2])));
                }
            }
            return result;
        }

        private bool ShouldIncludeUser(string userId, string groupName, List<KeyValuePair<string, KeyValuePair<string, string>>> filterMap, ADStub adStub, ConcurrentDictionary<string, UserCache> userCache, List<string> attributeNamesToLoad)
        {
            // Find matching filters for this group
            var matchingFilters = filterMap.Where(filter =>
                IsWildcardOrRegexMatch(groupName, filter.Key)).ToList();

            if (!matchingFilters.Any())
            {
                return true; // No filters = include user
            }

            // Check if user matches ALL applicable filters using the cache
            foreach (var filter in matchingFilters)
            {
                string attributeName = filter.Value.Key;
                string filterPattern = filter.Value.Value;

                string userAttributeValue = adStub.GetUserAttribute(userId, attributeName, userCache, attributeNamesToLoad);

                if (!IsWildcardOrRegexMatch(userAttributeValue, filterPattern))
                {
                    log.Debug($"User {userId} does not match filter for group {groupName}. Attribute {attributeName} = '{userAttributeValue}', expected pattern: '{filterPattern}'");
                    return false; // User doesn't match this filter
                }
            }

            return true; // User matches all filters
        }

        private bool IsWildcardOrRegexMatch(string value, string pattern)
        {
            if (string.IsNullOrEmpty(value))
            {
                return false;
            }

            // Check exact match first (fastest)
            if (string.Equals(value, pattern, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            // Check if it's a regex pattern
            if (IsRegexPattern(pattern))
            {
                try
                {
                    return Regex.IsMatch(value, pattern, RegexOptions.IgnoreCase);
                }
                catch (ArgumentException ex)
                {
                    log.Warn($"Invalid regex pattern '{pattern}': {ex.Message}");
                    return false;
                }
            }

            // Fall back to simple wildcard matching if it contains * but isn't regex
            if (pattern.Contains("*"))
            {
                if (pattern.StartsWith("*") && pattern.EndsWith("*"))
                {
                    string middle = pattern.Substring(1, pattern.Length - 2);
                    return value.ToLower().Contains(middle.ToLower());
                }
                else if (pattern.StartsWith("*"))
                {
                    string suffix = pattern.Substring(1);
                    return value.ToLower().EndsWith(suffix.ToLower());
                }
                else if (pattern.EndsWith("*"))
                {
                    string prefix = pattern.Substring(0, pattern.Length - 1);
                    return value.ToLower().StartsWith(prefix.ToLower());
                }
            }

            return false;
        }

        private bool IsRegexPattern(string pattern)
        {
            // If it contains regex special chars, treat as regex
            char[] regexChars = { '^', '$', '[', ']', '(', ')', '{', '}', '+', '?', '|', '\\', '.' };
            return regexChars.Any(c => pattern.Contains(c));
        }

    }
}
