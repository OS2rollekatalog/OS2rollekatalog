using ADSyncService.Email;
using Microsoft.Graph.Models;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.AccountManagement;
using System.Linq;

namespace ADSyncService
{
    class UserCache
    {
        public bool IsActive { get; set; }
        public bool HasCpr { get; set; }
        public Dictionary<string, string> Attributes { get; set; } = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    }
    class ADStub
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private EmailService emailService = EmailService.Instance;
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        private UserCache GetOrLoadUser(string userId, string cprAttribute, ConcurrentDictionary<string, UserCache> userCache, IEnumerable<string> attributeNamesToLoad)
        {
            return userCache.GetOrAdd(userId, _ =>
            {
                using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
                using (UserPrincipal user = UserPrincipal.FindByIdentity(context, userId))
                {
                    if (user == null)
                        return new UserCache { IsActive = false, HasCpr = false };

                    using (DirectoryEntry de = (DirectoryEntry)user.GetUnderlyingObject())
                    {
                        var cache = new UserCache();
                        cache.IsActive = user.Enabled.HasValue && user.Enabled.Value;

                        var cpr = de.Properties[cprAttribute]?.Value as string;
                        cache.HasCpr = !string.IsNullOrEmpty(cpr) && cpr.Length >= 10 && cpr.Length <= 11;

                        foreach (var attr in attributeNamesToLoad)
                        {
                            if (de.Properties.Contains(attr))
                                cache.Attributes[attr] = de.Properties[attr].Value?.ToString();
                        }
                        return cache;
                    }
                }
            });
        }

        // note, the groupInGroup parameter should only be true when doing an initial import - when making membershipSync updates
        // the group in group thing is way to complex to solve here (and introduces nasty side-effects if actually implemented)
        public List<string> GetGroupMembers(string groupId, string groupName, bool groupInGroup = false)
        {
            log.Debug("Looking for members of " + groupName);

            List<string> members = new List<string>();

            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, groupId))
                {
                    if (group == null)
                    {
                        log.Warn("Group with id " + groupId + " and name " + groupName + " does not exist");
                        return null;
                    }
                    else
                    {
                        // argument to GetMembers indicate we want direct members, not indirect members
                        using (var groupMembers = group.GetMembers(groupInGroup))
                        {
                            foreach (Principal member in groupMembers)
                            {
                                if (!(member is UserPrincipal))
                                {
                                    continue;
                                }

                                members.Add(member.SamAccountName.ToLower());
                            }
                        }
                    }
                }
            }

            return members;
        }

        public List<Group> GetAllGroups(string ouDn, string nameAttribute)
        {
            List<Group> res = new List<Group>();

            bool subSearch = false;
            if (ouDn.EndsWith("*"))
            {
                subSearch = true;
                ouDn = ouDn.Substring(0, ouDn.Length - 1);
            }

            using (PrincipalContext context = new PrincipalContext(ContextType.Domain, null, ouDn))
            {
                GroupPrincipal template = new GroupPrincipal(context);
                using (PrincipalSearcher searcher = new PrincipalSearcher(template))
                {
                    if (subSearch)
                    {
                        ((DirectorySearcher)searcher.GetUnderlyingSearcher()).SearchScope = SearchScope.Subtree;
                    }
                    else
                    {
                        ((DirectorySearcher)searcher.GetUnderlyingSearcher()).SearchScope = SearchScope.OneLevel;
                    }

                    log.Info("Searching in " + ouDn);

                    using (var result = searcher.FindAll())
                    {
                        foreach (var group in result)
                        {
                            GroupPrincipal groupPrincipal = group as GroupPrincipal;
                            if (groupPrincipal != null)
                            {
                                DirectoryEntry dir = group.GetUnderlyingObject() as DirectoryEntry;
                                Group g = new Group();
                                g.Uuid = groupPrincipal.Guid.ToString().ToLower();
                                g.Name = getNameAttribute(dir, nameAttribute);


                                string description = groupPrincipal.Description != null ? groupPrincipal.Description : "";
                                if (remoteConfigurationService.GetConfiguration().includeNotesInDescription)
                                {
                                    string notes = dir.Properties["info"]?.Value?.ToString() ?? "";
                                    if (!string.IsNullOrWhiteSpace(notes))
                                    {
                                        description += "\n" + notes;
                                    }
                                }

                                g.Description = description;

                                res.Add(g);
                            }
                        }
                    }
                }
            }

            return res;
        }

        private string getNameAttribute(DirectoryEntry dir, string nameAttribute)
        {
            if (!string.IsNullOrEmpty(nameAttribute))
            {
                if (dir.Properties.Contains(nameAttribute)) {
                    return dir.Properties[nameAttribute].Value as string;
                }
            }

            return dir.Properties["name"].Value as string;
        }

        public void UpdateAttribute(string userId, string attributeName, string attributeValue)
        {
            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (UserPrincipal user = UserPrincipal.FindByIdentity(context, userId))
                {
                    using (DirectoryEntry directoryEntry = (DirectoryEntry) user.GetUnderlyingObject())
                    {
                        log.Info($"Setting {attributeName} attribute of {userId} to {attributeValue}");
                        if (directoryEntry.Properties.Contains(attributeName))
                        {
                            directoryEntry.Properties[attributeName].Value = attributeValue;
                        }
                        else
                        {
                            directoryEntry.Properties[attributeName].Add(attributeValue);
                        }
                        directoryEntry.CommitChanges();
                    }
                }
            }

        }

        public void ClearAttribute(string userId, string attributeName)
        {
            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (UserPrincipal user = UserPrincipal.FindByIdentity(context, userId))
                {
                    using (DirectoryEntry directoryEntry = (DirectoryEntry)user.GetUnderlyingObject())
                    {
                        log.Info($"clearing {attributeName} attribute of {userId}");
                        if (directoryEntry.Properties.Contains(attributeName))
                        {
                            directoryEntry.Properties[attributeName].Clear();
                            directoryEntry.CommitChanges();
                        }
                    }
                }
            }
        }

        public class Group
        {
            public string Uuid { get; set; }
            public string Name { get; set; }
            public string Description { get; set; }
        }

        public void AddMember(string groupId, string userId)
        {
            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (UserPrincipal user = UserPrincipal.FindByIdentity(context, userId))
                {
                    using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, groupId))
                    {
                        if (user == null || group == null)
                        {
                            log.Error("User or group does not exist: " + userId + " / " + groupId);
                            emailService.EnqueueMail("User or group does not exist: " + userId + " / " + groupId);
                            return;
                        }

                        log.Info("Added " + userId + " to " + group.Name);

                        try
                        {
                            group.Members.Add(user);
                            group.Save();
                        }
                        catch (PrincipalExistsException)
                        {
                            log.Warn("User " + userId + " was already a member of the group");
                        }
                    }
                }
            }
        }

        public void RemoveMember(string groupId, string userId)
        {
            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (UserPrincipal user = UserPrincipal.FindByIdentity(context, userId))
                {
                    using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, groupId))
                    {
                        if (user == null || group == null)
                        {
                            log.Error("User or group does not exist: " + userId + " / " + groupId);
                            emailService.EnqueueMail("User or group does not exist: " + userId + " / " + groupId);
                            return;
                        }

                        if (group.Members.Remove(user))
                        {
                            log.Info("Removed " + userId + " from " + group.Name);
                            group.Save();
                        }
                        else
                        {
                            log.Warn("Could not remove " + userId + " from " + group.Name);
                        }
                    }
                }
            }
        }

        public void CreateGroup(string systemRoleIdentifier, string itSystemIdentifier, string adGroupType, bool universel, string description)
        {
            string groupOU = remoteConfigurationService.GetConfiguration().createDeleteFeatureOU;
            string contextPath = "OU=" + itSystemIdentifier + "," + groupOU;

            // make sure contextPath exists
            if (!DirectoryEntry.Exists("LDAP://" + contextPath))
            {
                using (var de = new DirectoryEntry("LDAP://" + groupOU))
                {
                    using (DirectoryEntry child = de.Children.Add("OU=" + itSystemIdentifier, "OrganizationalUnit"))
                    {
                        child.CommitChanges();
                        log.Info("Created OU: " + contextPath);
                    }
                }
            }

            using (var de = new DirectoryEntry("LDAP://" + contextPath))
            {
                try
                {
                    using (var existingGroup = de.Children.Find("CN=" + systemRoleIdentifier, "group"))
                    {
                        if (existingGroup != null)
                        {
                            log.Warn("Could not create new distribution group " + systemRoleIdentifier + " because it already exists: " + existingGroup.Name + "," + contextPath);
                        }
                    }
                }
                catch (System.DirectoryServices.DirectoryServicesCOMException)
                {
                    using (DirectoryEntry dGroup = de.Children.Add("CN=" + systemRoleIdentifier, "group"))
                    {
                        dGroup.Properties["sAMAccountName"].Add(systemRoleIdentifier);

                        string createdType = "";
                        if ("DISTRIBUTION".Equals(adGroupType))
                        {
                            if (universel)
                            {
                                dGroup.Properties["groupType"].Add(unchecked((int)0x00000008));
                                createdType = "Universal Distribution";
                            }
                            else
                            {
                                dGroup.Properties["groupType"].Add(unchecked((int)0x00000002));
                                createdType = "Global Distribution";
                            }
                        }
                        else
                        {
                            if (universel)
                            {
                                dGroup.Properties["groupType"].Add(unchecked((int)0x80000008));
                                createdType = "Universal Security";
                            }
                            else
                            {
                                dGroup.Properties["groupType"].Add(unchecked((int)0x80000002));
                                createdType = "Global Security";
                            }
                        }

                        if (!string.IsNullOrEmpty(description))
                        {
                            dGroup.Properties["description"].Add(description);
                        }

                        dGroup.CommitChanges();

                        log.Info("Created group: " + systemRoleIdentifier + " in " + contextPath + " of type: " + createdType);
                    }
                }
            }
        }

        public bool HasCpr(string userId)
        {
            string cprAttribute = remoteConfigurationService.GetConfiguration().membershipSyncFeatureCprAttribute;
            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (UserPrincipal user = UserPrincipal.FindByIdentity(context, userId))
                {
                    using (DirectoryEntry de = user.GetUnderlyingObject() as DirectoryEntry)
                    {
                        if (de != null)
                        {
                            var cpr = de.Properties[cprAttribute].Value as string;

                            if (!string.IsNullOrEmpty(cpr) && cpr.Length >= 10 && cpr.Length <= 11)
                            {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }

        public void DeleteGroup(string systemRoleIdentifier, string itSystemIdentifier)
        {
            string groupOU = remoteConfigurationService.GetConfiguration().createDeleteFeatureOU;
            string contextPath = "OU=" + itSystemIdentifier + "," + groupOU;

            using (PrincipalContext context = new PrincipalContext(ContextType.Domain, null, contextPath))
            {
                using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, systemRoleIdentifier))
                {
                    if (group != null)
                    {
                        group.Delete();

                        log.Info("Deleted security group: " + systemRoleIdentifier + " in " + contextPath);
                    }
                }
            }
        }

        public bool EntityExistsInAD(string distinguishedName)
        {
            try
            {
                return DirectoryEntry.Exists("LDAP://" + distinguishedName);
            } catch (Exception e)
            {
                log.Warn("InvaliddistinguishedName ", e);
                return false;
            }
        }

        public DateTime? GetGroupLastUpdated(string groupId)
        {
            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, groupId))
                {
                    if (group == null)
                    {
                        log.Debug("Failed to get whenchanged. Group does not exist: " + groupId);
                        return null;
                    }
                    else
                    {
                        using (DirectoryEntry directoryEntry = (DirectoryEntry)group.GetUnderlyingObject())
                        {
                            return (DateTime?)directoryEntry.Properties["whenchanged"].Value;
                        }
                    }
                }
            }
        }

        public bool IsUserActive(string userId)
        {
            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (UserPrincipal user = UserPrincipal.FindByIdentity(context, userId))
                {
                    if (user == null)
                    {
                        log.Debug($"User {userId} not found in Active Directory");
                        return false;
                    }

                    if (!user.Enabled.HasValue || !user.Enabled.Value)
                    {
                        log.Debug($"User {userId} is disabled");
                        return false;
                    }

                    return true;
                }
            }
        }

        public bool HasCpr(string userId, ConcurrentDictionary<string, UserCache> userCache, IEnumerable<string> attributeNamesToLoad)
        {
            string cprAttribute = remoteConfigurationService.GetConfiguration().membershipSyncFeatureCprAttribute;
            return GetOrLoadUser(userId, cprAttribute, userCache, attributeNamesToLoad).HasCpr;
        }

        public bool IsUserActive(string userId, ConcurrentDictionary<string, UserCache> userCache, IEnumerable<string> attributeNamesToLoad)
        {
            string cprAttribute = remoteConfigurationService.GetConfiguration().membershipSyncFeatureCprAttribute;
            return GetOrLoadUser(userId, cprAttribute, userCache, attributeNamesToLoad).IsActive;
        }

        public string GetUserAttribute(string userId, string attributeName, ConcurrentDictionary<string, UserCache> userCache, IEnumerable<string> attributeNamesToLoad)
        {
            string cprAttribute = remoteConfigurationService.GetConfiguration().membershipSyncFeatureCprAttribute;
            GetOrLoadUser(userId, cprAttribute, userCache, attributeNamesToLoad).Attributes.TryGetValue(attributeName, out var value);
            return value;
        }

    }
}
