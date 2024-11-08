using ADSyncService.Email;
using ADSyncService.Properties;
using Microsoft.Graph.Models;
using System;
using System.Collections.Generic;
using System.Collections.Specialized;
using System.Configuration;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ADSyncService
{
    class BackSyncService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static StringCollection ous = Properties.Settings.Default.BackSyncFeature_OUs;
        private static bool convertToUserRoles = Properties.Settings.Default.BackSyncFeature_CreateUserRoles;
        private static bool groupsInGroupOnSync = Properties.Settings.Default.BackSyncFeature_GroupsInGroupOnSync;
        private static EmailService emailService = EmailService.Instance;

        public static void SyncGroupsToRoleCatalogue(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            foreach (string ouRaw in ous)
            {
                try
                {
                    // need to support OUs with & char in the name and these need to be written as &amp; in xml config.
                    var ou = ouRaw.Replace("&amp;", "&");
                    string[] tokens = ou.Split(';');
                    if (tokens.Length != 2)
                    {
                        log.Warn("Invalid OU in backsync: " + ou);
                        continue;
                    }

                    string itSystemId = tokens[0];
                    string ouDn = tokens[1];

                    var groups = adStub.GetAllGroups(ouDn);
                    if (groups == null)
                    {
                        log.Warn("Got 0 groups from OU: " + ouDn);
                        continue;
                    }

                    ItSystemData itSystemData = roleCatalogueStub.GetItSystemData(itSystemId);
                    if (itSystemData == null)
                    {
                        log.Warn("Got no it-system from role catalogue: " + itSystemId);
                        continue;
                    }

                    bool changes = false;

                    // find to remove (or maybe update name)
                    for (int i = itSystemData.systemRoles.Count - 1; i >= 0; i--)
                    {
                        bool found = false;

                        foreach (var group in groups)
                        {
                            if (group.Uuid.Equals(itSystemData.systemRoles[i].identifier))
                            {
                                if (!group.Name.Equals(itSystemData.systemRoles[i].name))
                                {
                                    log.Info("Updating name on group to " + group.Name);
                                    itSystemData.systemRoles[i].name = group.Name;
                                    changes = true;
                                }

                                if (!group.Description.Equals(itSystemData.systemRoles[i].description))
                                {
                                    log.Info("Updating description on group " + group.Name);
                                    itSystemData.systemRoles[i].description = group.Description;
                                    changes = true;
                                }

                                found = true;
                                break;
                            }
                        }

                        if (!found)
                        {
                            log.Info("Removing " + itSystemData.systemRoles[i].name + " from " + itSystemData.name);

                            itSystemData.systemRoles.RemoveAt(i);
                            changes = true;
                        }
                    }

                    // find to create
                    foreach (var group in groups)
                    {
                        bool found = false;
                        SystemRole existingRole = null;

                        foreach (var role in itSystemData.systemRoles)
                        {
                            if (group.Uuid.Equals(role.identifier))
                            {
                                found = true;
                                existingRole = role;
                                break;
                            }
                        }

                        if (!found)
                        {
                            SystemRole systemRole = new SystemRole();
                            systemRole.description = group.Description;
                            systemRole.identifier = group.Uuid;
                            systemRole.name = group.Name;
                            systemRole.users = new List<string>();
                            itSystemData.systemRoles.Add(systemRole);

                            // add members
                            List<string> members = adStub.GetGroupMembers(group.Uuid, groupsInGroupOnSync);
                            if (members != null)
                            {
                                foreach (var member in members)
                                {
                                    systemRole.users.Add(member);
                                }
                            }

                            log.Info("Adding " + group.Name + " to " + itSystemData.name);
                            changes = true;
                        }
                        else if (ReImportUsersEnabled())
                        {
                            List<string> members = adStub.GetGroupMembers(group.Uuid, groupsInGroupOnSync);
                            if (members != null)
                            {
                                log.Info("Re-importing users to " + itSystemData.name);
                                existingRole.users = members;
                                changes = true;
                            }
                        }
                    }

                    if (changes)
                    {
                        log.Info("Updating " + itSystemData.name);

                        itSystemData.convertRolesEnabled = convertToUserRoles;

                        roleCatalogueStub.SetItSystemData(itSystemId, itSystemData);
                    }
                }
                catch (Exception ex)
                {
                    if (ex is System.DirectoryServices.AccountManagement.PrincipalOperationException)
                    {
                        log.Error("Unable to find OU with dn (skipping): " + ouRaw, ex);
                        emailService.EnqueueMail("Unable to find OU with dn (skipping): " + ouRaw, ex);
                    }
                    else
                    {
                        throw ex;
                    }
                }
            }
        }

        private static bool ReImportUsersEnabled()
        {
            string reImportUsers = ConfigurationManager.AppSettings["ReImportUsers"];
            return reImportUsers != null && reImportUsers.Equals("Yes");
        }
    }
}
