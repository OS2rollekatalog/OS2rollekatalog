
using ADSyncService.Email;
using System;
using System.Net;

namespace ADSyncService
{
    class Program
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
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
                log.Error(ex);
                emailService.EnqueueMail("Error occurred in ADSyncService", ex);
            }
        }

        private static void CurrentDomain_UnhandledException(object sender, UnhandledExceptionEventArgs e)
        {
            if (e != null && e.ExceptionObject != null)
            {
                log.Error((Exception)e.ExceptionObject);
                emailService.EnqueueMail("UnhandledException found.", (Exception)e.ExceptionObject);
            }
        }
    }
}
