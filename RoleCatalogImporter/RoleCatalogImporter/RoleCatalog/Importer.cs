using System;
using RestSharp;
using System.Net;
using Newtonsoft.Json;

namespace RoleCatalogImporter
{
    class Importer
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static Uri uri = new Uri(Properties.Settings.Default.ApiUrl);
        private static string apiKey = Properties.Settings.Default.ApiKey;

        public Importer()
        {
            ServicePointManager.ServerCertificateValidationCallback += (sender, certificate, chain, sslPolicyErrors) => true;
        }

        public bool Import(Organisation organisation)
        {
            try
            {
                RestClient client = new RestClient(uri.Scheme + "://" + uri.Host + ":" + uri.Port);

                var request = new RestRequest(uri.AbsolutePath, Method.POST);
                request.RequestFormat = DataFormat.Json;
                request.AddHeader("Content-Type", "application/json");
                request.AddHeader("ApiKey", apiKey);

                //fix from https://bytefish.de/blog/restsharp_custom_json_serializer/
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;
                request.AddJsonBody(organisation);

                var result = client.Execute(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Info(result.Content);
                    return true;
                }

                log.Error("Import failed (" + result.StatusCode + ") : " + result.Content);
            }
            catch (Exception ex)
            {
                log.Error("Import failed", ex);
            }

            return false;
        }
    }
}
