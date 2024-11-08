using ADSyncService.Email;
using System;
using System.IO;
using System.Net.Http;

namespace ADSyncService
{
    class LogUploaderService
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static bool enabled = Properties.Settings.Default.LogUploaderEnabled;
        private static string url = Properties.Settings.Default.LogUploaderFileShareUrl;
        private static string apiKey = Properties.Settings.Default.LogUploaderFileShareApiKey;
        private static string domain = Properties.Settings.Default.Domain;

        private RoleCatalogueStub roleCatalogueStub = new RoleCatalogueStub();
        private EmailService emailService = EmailService.Instance;

        // has to be the same as the path specified in the log section in appsettings.json minus the file name
        private readonly string logFilePath = "C:/logs/ADSyncService/system.log";

        public void CheckForLogRequest()
        {
            if (enabled)
            {
                try
                {
                    bool shouldUploadLog = roleCatalogueStub.GetShouldUploadLog();
                    if (shouldUploadLog)
                    {
                        log.Info("Logfile has been requested. Will attempt to upload logfile for today.");
                        string dateForLogFile = FindDateForLogFile();
                        FileStream stream = File.Open(logFilePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                        UploadFile(domain + "_log_" + dateForLogFile + ".log", stream);
                    }
                }
                catch (Exception ex)
                {
                    log.Error("Failed to upload requested logfile", ex);
                    emailService.EnqueueMail("Failed to upload requested logfile", ex);
                }
            }
        }

        private static string FindDateForLogFile()
        {
            DateTime today = DateTime.Today;
            string formattedDate = today.ToString("yyyyMMdd");
            return formattedDate;
        }

        public void UploadFile(string fileName, Stream fileStream)
        {

            byte[] payload = null;
            using (var memoryStream = new MemoryStream())
            {
                fileStream.CopyTo(memoryStream);
                payload = memoryStream.ToArray();
            }

            var client = new HttpClient();
            HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Post, $"{url}/files?name={fileName}");
            request.Headers.Add("ApiKey", apiKey);
            request.Content = new ByteArrayContent(payload);

            var response = client.SendAsync(request).Result;
            if (response.IsSuccessStatusCode)
            {
                log.Info("Upload file " + fileName + " with HTTP status: " + response.StatusCode);
            }
            else
            {
                log.Warn("Upload failed for " + fileName + " with HTTP status: " + response.StatusCode);
            }
        }
    }
}


