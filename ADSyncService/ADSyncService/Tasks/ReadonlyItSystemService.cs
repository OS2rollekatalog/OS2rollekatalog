using System.Collections.Specialized;
using System.Collections.Generic;

namespace ADSyncService
{
    class ReadonlyItSystemService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public void PerformUpdate(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            List<string> systemMap = remoteConfigurationService.GetConfiguration().readonlyItSystemFeatureSystemMap;
            foreach (string mapRaw in systemMap)
            {
                var map = mapRaw.Replace("&amp;", "&");
                string[] tokens = map.Split(';');
                if (tokens.Length != 2)
                {
                    log.Warn("Invalid entry in ItSystemGroup update: " + map);
                    continue;
                }

                string itSystemId = tokens[0];
                string ouDn = tokens[1];

                string nameAttribute = remoteConfigurationService.GetConfiguration().readonlyItSystemFeatureNameAttribute;
                var allGroups = adStub.GetAllGroups(ouDn, nameAttribute);
   
                ItSystemData itSystemData = roleCatalogueStub.GetItSystemData(itSystemId);
                if (itSystemData == null)
                {
                    log.Warn("Got no it-system from role catalogue: " + itSystemId);
                    continue;
                }

                // find to remove (or potentially update)
                for (int i = itSystemData.systemRoles.Count - 1; i >= 0; i--)
                {
                    bool found = false;
                    var systemRole = itSystemData.systemRoles[i];
                    systemRole.users = new List<string>();

                    foreach (var group in allGroups)
                    {
                        // update scenario
                        if (group.Uuid.Equals(systemRole.identifier))
                        {
                            found = true;

                            List<string> members = adStub.GetGroupMembers(group.Name);
                            if (members != null)
                            {
                                foreach (var member in members)
                                {
                                    systemRole.users.Add(member);
                                }
                            }
                            systemRole.name = group.Name;
                            systemRole.description = group.Description;

                            break;
                        }
                    }

                    // delete scenario
                    if (!found)
                    {
                        log.Info("Removing " + itSystemData.systemRoles[i].name + " from " + itSystemData.name);

                        itSystemData.systemRoles.RemoveAt(i);
                    }
                }

                // find to create
                foreach (var group in allGroups)
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

                        List<string> members = adStub.GetGroupMembers(group.Name);
                        if (members != null)
                        {
                            foreach (var member in members)
                            {
                                systemRole.users.Add(member);
                            }
                        }

                        itSystemData.systemRoles.Add(systemRole);

                        log.Info("Adding " + group.Name + " to " + itSystemData.name);
                    }
                }

                log.Info("Updating " + itSystemData.name);

                itSystemData.@readonly = true;
                itSystemData.convertRolesEnabled = true;

                roleCatalogueStub.SetItSystemData(itSystemId, itSystemData);
            }
        }
    }
}
