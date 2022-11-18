using Quartz;

namespace ADSyncService
{
    internal class ItSystemsGroupsJob : IJob
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
                    log.Info("Executing itSystemGroups");
                    ItSystemGroupService.PerformUpdate(roleCatalogueStub, adStub);
                    log.Info("Finished executing itSystemGroups");
                }
                catch (System.Exception ex)
                {
                    log.Error("Update of it-system groups failed", ex);
                }
            }
        }
    }
}