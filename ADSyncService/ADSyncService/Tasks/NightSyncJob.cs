using ADSyncService.Email;
using Quartz;
using System;

namespace ADSyncService
{
    internal class ItSystemsGroupsJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);

        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private ADStub adStub = new ADStub();
        private EmailService emailService = EmailService.Instance;
        private ItSystemGroupService itSystemGroupService = new ItSystemGroupService();
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public void Execute(IJobExecutionContext context)
        {
            RemoteConfiguration configuration = remoteConfigurationService.GetConfigurationIfAvailable();
            if (configuration == null)
            {
                log.Error("ItSystemsGroupsJob skipped: no valid configuration");
                return;
            }

            if (configuration.itSystemGroupFeatureEnabled)
            {
                try
                {
                    log.Info("Executing itSystemGroups");
                    itSystemGroupService.PerformUpdate(roleCatalogueStub, adStub);
                    log.Info("Finished executing itSystemGroups");
                }
                catch (System.Exception ex)
                {
                    log.Error("Update of it-system groups failed", ex);
                    emailService.EnqueueMail("Update of it-system groups failed", ex);
                }
            }
        }
    }
}