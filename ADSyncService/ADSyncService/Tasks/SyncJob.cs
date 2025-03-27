using ADSyncService.Email;
using ADSyncService.Persistance;
using Quartz;
using System;
using System.Threading;
using System.Threading.Tasks;

namespace ADSyncService
{
    [DisallowConcurrentExecution]
    internal class SyncJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);

        private RemoteConfigurationService remoteConfiguration = RemoteConfigurationService.Instance;
        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private EmailService emailService = EmailService.Instance;
        private ADStub adStub = new ADStub();
        private BackSyncService backSyncService = new BackSyncService();
        private CreateDeleteService createDeleteService = new CreateDeleteService();
        private MembershipSyncService membershipSyncService = new MembershipSyncService();
        private readonly PersistenceService persistenceService = new PersistenceService();

        public void Execute(IJobExecutionContext context)
        {
             bool membershipSyncEnabled = remoteConfiguration.GetConfiguration().membershipSyncFeatureEnabled;
             bool createDeleteEnabled = remoteConfiguration.GetConfiguration().createDeleteFeatureEnabled;
             bool backSyncEnabled = remoteConfiguration.GetConfiguration().backSyncFeatureEnabled;
            log.Info("membershipSyncEnabled: " + membershipSyncEnabled);
            log.Info("createDeleteEnabled: " + createDeleteEnabled);
            log.Info("backSyncEnabled: " + backSyncEnabled);

            if (createDeleteEnabled)
            {
                try
                {
                    createDeleteService.PerformGroupOperations(roleCatalogueStub, adStub);
                }
                catch (System.Exception ex)
                {
                    log.Error("Create/Delete of groups failed", ex);
                    emailService.EnqueueMail("Create/Delete of groups failed", ex);
                }
            }

            if (membershipSyncEnabled)
            {
                try
                {
                    // retrieve any changes from role catalogue
                    var syncData = roleCatalogueStub.GetSyncData(fullSync: false);
                    membershipSyncService.SynchronizeGroupMemberships(roleCatalogueStub, adStub, persistenceService, syncData);
                }
                catch (System.Exception ex)
                {
                    log.Error("Membership sync failed", ex);
                    emailService.EnqueueMail("Membership sync failed", ex);
                }
            }

            if (backSyncEnabled)
            {
                try
                {
                    backSyncService.SyncGroupsToRoleCatalogue(roleCatalogueStub, adStub);
                }
                catch (System.Exception ex)
                {
                    log.Error("BackSync failed", ex);
                    emailService.EnqueueMail("BackSync failed", ex);
                }
            }
        }
    }
}