using System;
using System.Collections.Generic;
using System.Collections.Specialized;
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

        public static void SyncGroupsToRoleCatalogue(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            foreach (string ou in ous)
            {
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

                    foreach (var role in itSystemData.systemRoles)
                    {
                        if (group.Uuid.Equals(role.identifier))
                        {
                            found = true;
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

                        // only add members on CREATE scenario
                        List<string> members = adStub.GetGroupMembers(group.Uuid);
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
                }

                if (changes)
                {
                    log.Info("Updating " + itSystemData.name);

                    itSystemData.convertRolesEnabled = convertToUserRoles;

                    roleCatalogueStub.SetItSystemData(itSystemId, itSystemData);
                }
            }
        }
    }
}
