using Azure.Core;
using Azure.Identity;
using Microsoft.Graph;
using Microsoft.Graph.Models;
using Microsoft.Graph.Authentication;
using System.Collections.Generic;
using Microsoft.Graph.Users.Item.SendMail;
using System;
using Microsoft.Graph.Users.Item.GetMailTips;
using System.Text;

namespace ADSyncService.Email
{
    public class EmailService
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static string tenantId = Properties.Settings.Default.TenantId;
        private static string clientId = Properties.Settings.Default.ClientId;
        private static string clientSecret = Properties.Settings.Default.ClientSecret;
        private static string user = Properties.Settings.Default.User;
        private static string recipientEmail = Properties.Settings.Default.RecipientEmail;
        private static bool featureEnabled = Properties.Settings.Default.SendErrorEmailFeature_Enabled;

        private static readonly Lazy<EmailService> lazy = new Lazy<EmailService>(() => new EmailService());
        public static EmailService Instance { get { return lazy.Value; } }

        private GraphServiceClient graphClient;
        private bool initialized = false;

        private static readonly Queue<SendMailPostRequestBody> emailQueue = new Queue<SendMailPostRequestBody>();

        private EmailService() {}

        public void init()
        {
            if (!featureEnabled)
            {
                log.Debug("Sending emails with error messages is DISABLED.");
                return;
            }

            if (String.IsNullOrEmpty(user) || String.IsNullOrEmpty(tenantId) || String.IsNullOrEmpty(clientId) || String.IsNullOrEmpty(clientSecret))
            {
                log.Error("Invalid email configuration.");
                //featureEnabled = false; //this would prevent message above from spamming
                return;
            }
            try
            {
                var clientSecretCredential = new ClientSecretCredential(tenantId, clientId, clientSecret);
                graphClient = new GraphServiceClient(clientSecretCredential);
                graphClient.Users[user].GetMailTips.PostAsGetMailTipsPostResponseAsync(new GetMailTipsPostRequestBody());

                initialized = true;
            }
            catch (Exception ex)
            {
                log.Error("GraphAPI connection failed", ex);
            }
        }

        public void EnqueueMail(string errorMessage, Exception e)
        {
            if (!featureEnabled) { return; }
            var requestBody = new SendMailPostRequestBody
            {
                Message = new Message
                {
                    Subject = "ADSyncService error log.",
                    Body = new ItemBody
                    {
                        ContentType = BodyType.Text,
                        Content = $"Der skete en fejl i ADSyncService:\r\n{errorMessage}\r\n{e.Message}\r\nKomplet stacktrace vedhæftet."
                    },
                    ToRecipients = new List<Recipient>
                    {
                        new Recipient
                        {
                            EmailAddress = new EmailAddress
                            {
                                Address = recipientEmail,
                            },
                        },
                    },
                    HasAttachments = true,
                    Attachments = new List<Attachment>
                    {
                        new FileAttachment
                        {
                            Name = $"stacktrace-{DateTime.Now.Date}.txt",
                            ContentBytes = Encoding.UTF8.GetBytes(e.StackTrace),
                            ContentType = "text/plain",
                            OdataType = "#microsoft.graph.fileAttachment"
                        }
                    }
                },
                SaveToSentItems = false,
            };

            emailQueue.Enqueue(requestBody);
            log.Debug($"Adding email to queue: {e.Message}");
        }
        public void EnqueueMail(string message)
        {
            if (!featureEnabled) { return; }
            var requestBody = new SendMailPostRequestBody
            {
                Message = new Message
                {
                    Subject = "ADSyncService error log.",
                    Body = new ItemBody
                    {
                        ContentType = BodyType.Text,
                        Content = $"Der skete en fejl i ADSyncService:\r\n{message}"
                    },
                    ToRecipients = new List<Recipient>
                    {
                        new Recipient
                        {
                            EmailAddress = new EmailAddress
                            {
                                Address = recipientEmail,
                            },
                        },
                    }
                },
                SaveToSentItems = false,
            };

            emailQueue.Enqueue(requestBody);
            log.Debug($"Adding email to queue: {message}");
        }

        public void SendPendingEmails()
        {
            if (!featureEnabled) { return; }
            if (!initialized) {  init(); }

            SendMailPostRequestBody email = null;
            lock (emailQueue)
            {
                if (emailQueue.Count > 0)
                {
                    email = emailQueue.Dequeue();
                }
            }

            if (email != null)
            {
                log.Debug($"Sending email: {email.Message.Subject}");
                var taskAwaitable = graphClient.Users[user].SendMail.PostAsync(email).ConfigureAwait(false);
                try
                {
                    taskAwaitable.GetAwaiter().GetResult();
                }
                catch (Exception ex)
                {
                    log.Error("Email sending failed", ex);
                }
            }
        }

    }
}
