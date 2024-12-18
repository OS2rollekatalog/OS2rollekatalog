using ADSyncService.Email;
using Quartz;
using System;

namespace ADSyncService
{
    [DisallowConcurrentExecution]
    internal class RemoteConfigurationJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private RemoteConfigurationService rcConfigurationService = RemoteConfigurationService.Instance;
        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private ADStub adStub = new ADStub();
        private EmailService emailService = EmailService.Instance;

        public void Execute(IJobExecutionContext context)
        {
            try
            {
                log.Info("Executing RemoteConfigurationJob");
                rcConfigurationService.FetchConfiguration(roleCatalogueStub, adStub);
                log.Info("Finished executing RemoteConfigurationJob");
            }
            catch (System.Exception ex)
            {
                log.Error("RemoteConfigurationJob failed", ex);
                emailService.EnqueueMail("RemoteConfigurationJobs failed", ex);
            }
        }
    }
}
