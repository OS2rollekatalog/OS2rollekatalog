using log4net.Config;
using Quartz;
using Quartz.Impl;
using System;
using System.IO;

namespace RoleCatalogImporter
{
    class Application
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private JobKey jobKey;
        private IScheduler schedule;

        public Application()
        {
            // configure logging
            Directory.SetCurrentDirectory(AppDomain.CurrentDomain.BaseDirectory);
            FileInfo logFile = new FileInfo("Log.config");
            XmlConfigurator.ConfigureAndWatch(logFile);

            // configure scheduled task
            ISchedulerFactory schedFact = new StdSchedulerFactory();

            schedule = schedFact.GetScheduler();
            schedule.Start();

            IJobDetail job = JobBuilder.Create<SyncJob>()
                .WithIdentity("mappingJob", "group1")
                .Build();
            jobKey = job.Key;

            ITrigger trigger = TriggerBuilder.Create()
              .WithIdentity("trigger", "group1")
              .WithCronSchedule(Properties.Settings.Default.CronSchedule)
              .StartNow()
              .Build();

            schedule.ScheduleJob(job, trigger);
            schedule.TriggerJob(job.Key);
        }

        public void Start()
        {
            log.Info("OS2rollekatalog service started");

            schedule.ResumeJob(jobKey);
        }

        public void Stop()
        {
            log.Info("OS2rollekatalog service stopped");

            schedule.PauseJob(jobKey);
        }
    }
}
