using RoleCatalogImporter.Email;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Text;
using System.Threading.Tasks;

namespace RoleCatalogImporter
{
    class Program
    {
        private static EmailService emailService = EmailService.Instance;
        static void Main(string[] args)
        {
            try
            {
                AppDomain.CurrentDomain.UnhandledException += CurrentDomain_UnhandledException;
                ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
                Configuration.Configure();
            }
            catch (Exception ex)
            {
                emailService.EnqueueMail("Error occurred in ADSyncService", ex);
            }
        }
        private static void CurrentDomain_UnhandledException(object sender, UnhandledExceptionEventArgs e)
        {
            if (e != null && e.ExceptionObject != null)
            {
                emailService.EnqueueMail("UnhandledException found.", (Exception)e.ExceptionObject);
            }
        }
    }
}
