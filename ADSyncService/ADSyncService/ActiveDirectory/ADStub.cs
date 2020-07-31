using System;
using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.AccountManagement;
using System.Web.UI.WebControls;

namespace ADSyncService
{
    class ADStub
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static string groupOU = Properties.Settings.Default.GroupOU;

        public List<string> GetGroupMembers(string groupId)
        {
            List<string> members = new List<string>();

            using (PrincipalContext context = new PrincipalContext(ContextType.Domain))
            {
                using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, groupId))
                {
                    if (group == null)
                    {
                        log.Error("Group does not exist: " + groupId);
                    }
                    else
                    {
                        // argument to GetMembers indicate we want direct members, not indirect members
                        foreach (Principal member in group.GetMembers(false))
                        {
                            members.Add(member.SamAccountName.ToLower());
                        }
                    }
                }
            }

            return members;
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

                        log.Info("Added " + userId + " to " + groupId);

                        group.Members.Add(user);
                        group.Save();
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
                            log.Info("Removed " + userId + " from " + groupId);
                            group.Save();
                        }
                        else
                        {
                            log.Warn("Could not remove " + userId + " from " + groupId);
                        }
                    }
                }
            }
        }

        public void CreateGroup(string systemRoleIdentifier, string itSystemIdentifier)
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

            using (PrincipalContext context = new PrincipalContext(ContextType.Domain, null, contextPath))
            {
                using (GroupPrincipal group = GroupPrincipal.FindByIdentity(context, systemRoleIdentifier))
                {
                    if (group == null)
                    {
                        using (var groupPrincipal = new GroupPrincipal(context, systemRoleIdentifier))
                        {
                            groupPrincipal.Save();
                            log.Info("Created security group: " + systemRoleIdentifier + " in " + contextPath);
                        }
                    }
                    else
                    {
                        log.Warn("Could not create new security group " + systemRoleIdentifier + " because it already exists: " + group.DistinguishedName);
                    }
                }
            }
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
