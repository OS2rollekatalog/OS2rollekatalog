using ADSyncService.Email;
using ADSyncService.Persistance;
using Quartz;
using System;
using System.Threading;
using System.Threading.Tasks;

namespace ADSyncService
{
    [DisallowConcurrentExecution]
    internal class FullMembershipSyncJob : IJob
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
            bool fullMembershipSyncEnabled = remoteConfiguration.GetConfiguration().fullMembershipSyncFeatureEnabled;
            log.Info("fullMembershipSyncEnabled: " + fullMembershipSyncEnabled);

            if (fullMembershipSyncEnabled)
            {
                try
                {
                    // retrieve any changes from role catalogue
                    var fullSyncData = roleCatalogueStub.GetSyncData(fullSync: true);
                    membershipSyncService.SynchronizeAllGroupMemberships(roleCatalogueStub, adStub, persistenceService, fullSyncData);
                }
                catch (System.Exception ex)
                {
                    log.Error("Membership sync failed", ex);
                    emailService.EnqueueMail("Membership sync failed", ex);
                }
            }
        }
    }
}