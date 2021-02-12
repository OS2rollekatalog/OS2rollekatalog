using Quartz;

namespace ADSyncService
{
    internal class NightSyncJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool itSystemGroupsEnabled = Properties.Settings.Default.ItSystemGroupFeature_Enabled;

        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private ADStub adStub = new ADStub();

        public void Execute(IJobExecutionContext context)
        {
            if (itSystemGroupsEnabled)
            {
                try
                {
                    ItSystemGroupService.PerformUpdate(roleCatalogueStub, adStub);
                }
                catch (System.Exception ex)
                {
                    log.Error("Update of it-system groups failed", ex);
                }
            }
        }
    }
}