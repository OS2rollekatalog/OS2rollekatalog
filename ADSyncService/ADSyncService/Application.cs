using ADSyncService.Email;
using log4net.Config;
using Quartz;
using Quartz.Impl;
using Quartz.Listener;
using System;
using System.IO;
using System.Threading;

namespace ADSyncService
{
    class Application
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private JobKey jobKey;
        private IScheduler schedule;
        private readonly ManualResetEventSlim jobCompletedEvent = new ManualResetEventSlim(false);

        public Application()
        {
            // configure logging
            Directory.SetCurrentDirectory(AppDomain.CurrentDomain.BaseDirectory);
            FileInfo logFile = new FileInfo("Log.config");
            XmlConfigurator.ConfigureAndWatch(logFile);

            // configure scheduled task
            ISchedulerFactory schedFact = new StdSchedulerFactory();
            schedule = schedFact.GetScheduler();
            schedule.ListenerManager.AddJobListener(new RemoteConfigurationJobListener(jobCompletedEvent));
            schedule.Start();

            // RemoteConfiguration job
            IJobDetail remoteConfigurationJob = JobBuilder.Create<RemoteConfigurationJob>()
                .WithIdentity("remoteConfigurationJob", "group6")
                .Build();

            ITrigger remoteConfigurationTrigger = TriggerBuilder.Create()
                .WithIdentity("trigger6", "group6")
                .WithSimpleSchedule(x => x
                    .WithIntervalInMinutes(5)
                    .RepeatForever())
                .Build();

            schedule.ScheduleJob(remoteConfigurationJob, remoteConfigurationTrigger);

            log.Info("Running remoteConfigurationJob first...");
            schedule.TriggerJob(remoteConfigurationJob.Key);

            jobCompletedEvent.Wait();

            log.Info("remoteConfigurationJob completed. Scheduling other jobs...");

            ScheduleOtherJobs();
        }

        private void ScheduleOtherJobs()
        {
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
            var itSystemsGroupsCron = String.IsNullOrEmpty(Properties.Settings.Default.ItSystemGroupFeature_Cron)
                ? $"0 {random.Next(0, 59)} 2 ? * *"
                : Properties.Settings.Default.ItSystemGroupFeature_Cron;
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

            // logUploader job
            IJobDetail logUploaderJob = JobBuilder.Create<LogUploaderJob>()
                .WithIdentity("logUploaderJob", "group4")
                .Build();
            ITrigger logUploaderTrigger = TriggerBuilder.Create()
                .WithIdentity("trigger4", "group4")
                .WithSimpleSchedule(x => x
                    .WithIntervalInMinutes(5)
                    .RepeatForever())
                .StartNow()
                .Build();

            schedule.ScheduleJob(logUploaderJob, logUploaderTrigger);
            schedule.TriggerJob(logUploaderJob.Key);

            // email send task
            IJobDetail errorLogEmailJob = JobBuilder.Create<EmailJob>()
                .WithIdentity("errorLogEmailJob", "group5")
                .Build();

            ITrigger errorLogEmailTrigger = TriggerBuilder.Create()
                .WithIdentity("trigger5", "group5")
                .WithSimpleSchedule(x => x
                    .WithIntervalInMinutes(15)
                    .RepeatForever())
                .StartNow()
                .Build();

            schedule.ScheduleJob(errorLogEmailJob, errorLogEmailTrigger);
            schedule.TriggerJob(errorLogEmailJob.Key);
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

    public class RemoteConfigurationJobListener : JobListenerSupport
    {
        private readonly ManualResetEventSlim jobCompletedEvent;

        public RemoteConfigurationJobListener(ManualResetEventSlim jobCompletedEvent)
        {
            this.jobCompletedEvent = jobCompletedEvent;
        }

        public override string Name => "RemoteConfigurationJobListener";

        public override void JobWasExecuted(IJobExecutionContext context, JobExecutionException jobException)
        {
            if (context.JobDetail.Key.Name == "remoteConfigurationJob")
            {
                jobCompletedEvent.Set();
            }
        }
    }
}
