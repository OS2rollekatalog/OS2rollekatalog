using Quartz;

namespace ADSyncService
{
    [DisallowConcurrentExecution]
    public class LogUploaderJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);

        private LogUploaderService logUploaderService = new LogUploaderService();
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public void Execute(IJobExecutionContext context)
        {
            bool logUploaderEnabled = remoteConfigurationService.GetConfiguration().logUploaderEnabled;
            if (logUploaderEnabled)
            {
                logUploaderService.CheckForLogRequest();
            }
        }
    }
}