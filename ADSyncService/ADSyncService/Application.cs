using log4net.Config;
using Quartz;
using Quartz.Impl;
using System;
using System.IO;

namespace ADSyncService
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

            IJobDetail job2 = JobBuilder.Create<NightSyncJob>()
                .WithIdentity("mappingJob2", "group2")
                .Build();
            jobKey = job.Key;

            Random random = new Random();
            ITrigger trigger2 = TriggerBuilder.Create()
              .WithIdentity("trigger2", "group2")
              .WithCronSchedule("0 " + random.Next(0, 59) + " 2 ? * *")
              .StartNow()
              .Build();

            schedule.ScheduleJob(job2, trigger2);
            schedule.TriggerJob(job2.Key);
        }

        public void Start()
        {
            log.Info("ADSyncService service started");

            schedule.ResumeJob(jobKey);
        }

        public void Stop()
        {
            log.Info("ADSyncService service stopped");

            schedule.PauseJob(jobKey);
        }
    }
}
