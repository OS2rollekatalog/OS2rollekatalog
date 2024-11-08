using Microsoft.Graph.Users.Item.SendMail;
using Quartz;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ADSyncService.Email
{
    [DisallowConcurrentExecution]
    internal class EmailJob : IJob
    {
        private EmailService emailService = EmailService.Instance;

        public void Execute(IJobExecutionContext context)
        {
            emailService.SendPendingEmails();
        }
    }
}
