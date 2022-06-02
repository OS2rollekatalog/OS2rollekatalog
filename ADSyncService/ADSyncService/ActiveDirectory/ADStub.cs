using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.AccountManagement;
using System.Linq;

namespace ADSyncService
{
    class ADStub
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static string groupOU = Properties.Settings.Default.CreateDeleteFeature_OU;
        private static string cprAttribute = Properties.Settings.Default.MembershipSyncFeature_CprAttribute;

        public List<string> GetGroupMembers(string groupId)
        {
            log.Debug("Looking for members of " + groupId);

            List<string> members = new List<string>();

            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, groupId))
                {
                    if (group == null)
                    {
                        log.Error("Group does not exist: " + groupId);
                        return null;
                    }
                    else
                    {
                        // argument to GetMembers indicate we want direct members, not indirect members
                        foreach (Principal member in group.GetMembers(false))
                        {
                            if (member is GroupPrincipal)
                            {
                                continue;
                            }

                            members.Add(member.SamAccountName.ToLower());
                        }
                    }
                }
            }

            return members;
        }

        public List<Group> GetAllGroups(string ouDn)
        {
            List<Group> res = new List<Group>();

            using (PrincipalContext context = new PrincipalContext(ContextType.Domain, null, ouDn))
            {
                GroupPrincipal template = new GroupPrincipal(context);
                using (PrincipalSearcher searcher = new PrincipalSearcher(template))
                {
                    ((DirectorySearcher)searcher.GetUnderlyingSearcher()).SearchScope = SearchScope.OneLevel;

                    using (var result = searcher.FindAll())
                    {
                        foreach (var group in result)
                        {
                            GroupPrincipal groupPrincipal = group as GroupPrincipal;
                            if (groupPrincipal != null)
                            {
                                Group g = new Group();
                                g.Uuid = groupPrincipal.Guid.ToString().ToLower();
                                g.Name = groupPrincipal.Name;
                                g.Description = (groupPrincipal.Description != null) ? ((groupPrincipal.Description.Length > 200) ? groupPrincipal.Description.Substring(0, 200) : groupPrincipal.Description) : "";

                                res.Add(g);
                            }
                        }
                    }
                }
            }

            return res;
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

        public void CreateGroup(string systemRoleIdentifier, string itSystemIdentifier, string adGroupType, bool universel)
        {
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

                        dGroup.CommitChanges();

                        log.Info("Created group: " + systemRoleIdentifier + " in " + contextPath + " of type: " + createdType);
                    }
                }
            }
        }

        public bool HasCpr(string userId)
        {
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
    }
}
