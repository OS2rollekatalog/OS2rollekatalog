
using ADSyncService.Email;

namespace ADSyncService
{
    class CreateDeleteService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static EmailService emailService = EmailService.Instance;
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public void PerformGroupOperations(RoleCatalogueStub roleCatalogueStub, ADStub adStub)
        {
            bool createGroupsEnabled = remoteConfigurationService.GetConfiguration().createDeleteFeatureCreateEnabled;
            bool deleteGroupsEnabled = remoteConfigurationService.GetConfiguration().createDeleteFeatureDeleteEnabled;
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
                                adStub.CreateGroup(operation.systemRoleIdentifier, operation.itSystemIdentifier, operation.adGroupType, operation.universal);
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
                            emailService.EnqueueMail("Failed to create group: " + operation.systemRoleIdentifier, ex);
                        }
                        else
                        {
                            log.Error("Failed to delete group: " + operation.systemRoleIdentifier + ". Cause: " + ex.Message, ex);
                            emailService.EnqueueMail("Failed to delete group: " + operation.systemRoleIdentifier, ex);
                        }
                    }
                }

                // inform rolecatalogue that we are done performing the operations
                roleCatalogueStub.ResetOperationHead(operationData.head);
            }
        }
    }
}
