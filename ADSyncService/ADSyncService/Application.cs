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

            IJobDetail itSystemsGroupsJob = JobBuilder.Create<ItSystemsGroupsJob>()
                .WithIdentity("mappingJob2", "group2")
                .Build();

            Random random = new Random();
            var itSystemsGroupsCron = String.IsNullOrEmpty(Properties.Settings.Default.ItSystemGroupFeature_Cron) ? $"0 {random.Next(0, 59)} 2 ? * *" : Properties.Settings.Default.ItSystemGroupFeature_Cron;
            ITrigger trigger2 = TriggerBuilder.Create()
              .WithIdentity("trigger2", "group2")
              .WithCronSchedule(itSystemsGroupsCron)
              .StartNow()
              .Build();

            schedule.ScheduleJob(itSystemsGroupsJob, trigger2);
            schedule.TriggerJob(itSystemsGroupsJob.Key);

            IJobDetail job3 = JobBuilder.Create<ReadOnlyItSystemSyncJob>()
                .WithIdentity("mappingJob3", "group3")
                .Build();

            ITrigger trigger3 = TriggerBuilder.Create()
              .WithIdentity("trigger3", "group3")
              .WithCronSchedule("0 " + random.Next(0, 59) + " 5,12,17 ? * *")
              .StartNow()
              .Build();

            schedule.ScheduleJob(job3, trigger3);
            schedule.TriggerJob(job3.Key);
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
