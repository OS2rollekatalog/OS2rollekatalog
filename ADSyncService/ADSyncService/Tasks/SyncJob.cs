using Quartz;
using System.Collections.Generic;

namespace ADSyncService
{
    internal class SyncJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private ADStub adStub = new ADStub();
        private static bool createGroupsEnabled = Properties.Settings.Default.CreateGroups;
        private static bool deleteGroupsEnabled = Properties.Settings.Default.DeleteGroups;

        public void Execute(IJobExecutionContext context)
        {
            try
            {
                PerformGroupOperations();

                SynchronizeGroupMemberships();
            }
            catch (System.Exception ex)
            {
                log.Error("Synchronization failed", ex);
            }
        }

        private void PerformGroupOperations()
        {
            var operationData = roleCatalogueStub.GetOperationData();

            // if we have pending operations, perform them
            if (operationData.head > 0)
            {
                log.Info("Found group operations (delete/create) for " + operationData.operations.Count + " group(s)");

                foreach (var operation in operationData.operations)
                {
                    try
                    {
                        if (operation.active)
                        {
                            if (createGroupsEnabled)
                            {
                                adStub.CreateGroup(operation.systemRoleIdentifier, operation.itSystemIdentifier);
                            }
                            else
                            {
                                log.Info("Skipping create of group " + operation.systemRoleIdentifier + " because group-create is disabled");
                            }
                        }
                        else
                        {
                            if (deleteGroupsEnabled)
                            {
                                adStub.DeleteGroup(operation.systemRoleIdentifier, operation.itSystemIdentifier);
                            }
                            else
                            {
                                log.Info("Skipping deletion of group " + operation.systemRoleIdentifier + " because group-delete is disabled");
                            }
                        }
                    }
                    catch (System.Exception ex)
                    {
                        if (operation.active)
                        {
                            log.Error("Failed to create group: " + operation.systemRoleIdentifier + ". Cause: " + ex.Message, ex);
                        }
                        else
                        {
                            log.Error("Failed to delete group: " + operation.systemRoleIdentifier + ". Cause: " + ex.Message, ex);
                        }
                    }
                }

                // inform rolecatalogue that we are done performing the operations
                roleCatalogueStub.ResetOperationHead(operationData.head);
            }
        }

        private void SynchronizeGroupMemberships()
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
                                adStub.RemoveMember(assignment.groupName, userId);
                                removed++;
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

        class AddAndRemoveSet
        {
            public List<string> usersToRemove { get; set; } = new List<string>();
            public List<string> usersToAdd { get; set; } = new List<string>();
        }
    }
}