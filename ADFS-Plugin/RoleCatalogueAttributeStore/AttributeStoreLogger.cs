using log4net;
using log4net.Appender;
using log4net.Layout;
using System.Diagnostics;
using System.Reflection;

namespace RoleCatalogueAttributeStore
{
    class AttributeStoreLogger
    {
        private static ILog log = LogManager.GetLogger(MethodBase.GetCurrentMethod().DeclaringType);
        private static Configuration configuration = Configuration.GetInstance();
        private static bool initialized = false;

        public static void Init()
        {
            if (!initialized)
            {
                PatternLayout patternLayout = new PatternLayout();
                patternLayout.ConversionPattern = "%date - %-5level - %message%newline";
                patternLayout.ActivateOptions();

                RollingFileAppender appender = new RollingFileAppender();
                appender.AppendToFile = true;
                appender.File = "c:\\logs\\rollekatalog\\system.log";
                appender.MaxFileSize = 10000000;
                appender.MaxSizeRollBackups = 5;
                appender.Layout = patternLayout;
                appender.ActivateOptions();

                var logRepository = (log4net.Repository.Hierarchy.Hierarchy)LogManager.GetRepository(Assembly.GetEntryAssembly());
                logRepository.Root.AddAppender(appender);
                logRepository.Root.Level = log4net.Core.Level.Debug;
                logRepository.Configured = true;

                initialized = true;
            }
        }

        public static void Warn(string message)
        {
            if (configuration.LogToEventLog)
            {
                EventLog.WriteEntry("OS2rollekatalog", message, EventLogEntryType.Warning);
            }
            else
            {
                log.Warn(message);
            }
        }

        public static void Info(string message)
        {
            if (configuration.LogToEventLog)
            {
                EventLog.WriteEntry("OS2rollekatalog", message, EventLogEntryType.Information);
            }
            else
            {
                log.Info(message);
            }
        }

        public static void Debug(string message)
        {
            if (Configuration.GetInstance().Debug)
            {
                if (configuration.LogToEventLog)
                {
                    EventLog.WriteEntry("OS2rollekatalog", message, EventLogEntryType.Information);
                }
                else
                {
                    log.Debug(message);
                }
            }
        }
    }
}
