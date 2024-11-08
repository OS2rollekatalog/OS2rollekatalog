using ADSyncService.Email;
using Quartz;

namespace ADSyncService
{
    public class ReadOnlyItSystemSyncJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool readonlyItSystemEnabled = Properties.Settings.Default.ReadonlyItSystemFeature_Enabled;

        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private ADStub adStub = new ADStub();
        private EmailService emailService = EmailService.Instance;

        public void Execute(IJobExecutionContext context)
        {
            if (readonlyItSystemEnabled)
            {
                try
                {
                    ReadonlyItSystemService.PerformUpdate(roleCatalogueStub, adStub);
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