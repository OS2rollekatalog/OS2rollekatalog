using ADSyncService.Email;
using Quartz;

namespace ADSyncService
{
    public class ReadOnlyItSystemSyncJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);

        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private ADStub adStub = new ADStub();
        private EmailService emailService = EmailService.Instance;
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;
        private ReadonlyItSystemService readonlyItSystemService = new ReadonlyItSystemService();

        public void Execute(IJobExecutionContext context)
        {
            bool readonlyItSystemEnabled = remoteConfigurationService.GetConfiguration().readonlyItSystemFeatureEnabled;
            if (readonlyItSystemEnabled)
            {
                try
                {
                    readonlyItSystemService.PerformUpdate(roleCatalogueStub, adStub);
                }
                catch (System.Exception ex)
                {
                    log.Error("Update of (readonly) it-system failed", ex);
                    emailService.EnqueueMail("Update of (readonly) it-system failed", ex);
                }
            }
        }
    }
}