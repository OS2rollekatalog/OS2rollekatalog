﻿using ADSyncService.Email;
using Quartz;
using System;
using System.Threading;
using System.Threading.Tasks;

namespace ADSyncService
{
    [DisallowConcurrentExecution]
    internal class SyncJob : IJob
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool membershipSyncEnabled = Properties.Settings.Default.MembershipSyncFeature_Enabled;
        private static bool createDeleteEnabled = Properties.Settings.Default.CreateDeleteFeature_Enabled;
        private static bool backSyncEnabled = Properties.Settings.Default.BackSyncFeature_Enabled;

        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private EmailService emailService = EmailService.Instance;
        private ADStub adStub = new ADStub();

        public void Execute(IJobExecutionContext context)
        {
            if (createDeleteEnabled)
            {
                try
                {
                    CreateDeleteService.PerformGroupOperations(roleCatalogueStub, adStub);
                }
                catch (System.Exception ex)
                {
                    log.Error("Create/Delete of groups failed", ex);
                    emailService.EnqueueMail("Create/Delete of groups failed", ex);
                }
            }

            if (membershipSyncEnabled)
            {
                try
                {
                    MembershipSyncService.SynchronizeGroupMemberships(roleCatalogueStub, adStub);
                }
                catch (System.Exception ex)
                {
                    log.Error("Membership sync failed", ex);
                    emailService.EnqueueMail("Membership sync failed", ex);
                }
            }

            if (backSyncEnabled)
            {
                try
                {
                    BackSyncService.SyncGroupsToRoleCatalogue(roleCatalogueStub, adStub);
                }
                catch (System.Exception ex)
                {
                    log.Error("BackSync failed", ex);
                    emailService.EnqueueMail("BackSync failed", ex);
                }
            }
        }
    }
}