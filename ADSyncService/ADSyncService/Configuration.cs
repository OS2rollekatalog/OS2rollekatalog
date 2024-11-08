using ADSyncService.Email;
using System;
using Topshelf;

namespace ADSyncService
{
    internal static class Configuration
    {
        private static EmailService emailService = EmailService.Instance;
        internal static void Configure()
        {
            HostFactory.Run(configure =>
            {
                configure.Service<Application>(service =>
                {
                    service.ConstructUsing(s => new Application());
                    service.WhenStarted(s => s.Start());
                    service.WhenStopped(s => s.Stop());
                });

                configure.RunAsLocalService();
                configure.SetServiceName("ADSyncService");
                configure.SetDisplayName("ADSyncService");
                configure.SetDescription("Synkroniserer AD gruppemedlemsskaber fra OS2rollekatalog");
                configure.OnException(e =>
                {
                    emailService.EnqueueMail("Error occurred in Topshelf", e);
                });
            });
        }
    }
}
