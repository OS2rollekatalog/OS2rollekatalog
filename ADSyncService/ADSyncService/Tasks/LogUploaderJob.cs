using Quartz;

namespace ADSyncService
{
    [DisallowConcurrentExecution]
    public class LogUploaderJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool logUploaderEnabled = Properties.Settings.Default.LogUploaderEnabled;

        private LogUploaderService logUploaderService = new LogUploaderService();

        public void Execute(IJobExecutionContext context)
        {
            if (logUploaderEnabled)
            {
                logUploaderService.CheckForLogRequest();
            }
        }
    }
}