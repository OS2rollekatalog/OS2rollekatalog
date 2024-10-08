﻿using RestSharp;
using System;
using System.Net;

namespace RoleCatalogImporter
{
    class Importer
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static Uri uri = new Uri(Properties.Settings.Default.ApiUrl);
        private static string apiKey = null;
        private static string domain = Properties.Settings.Default.Domain;

        public Importer()
        {
            ServicePointManager.ServerCertificateValidationCallback += (sender, certificate, chain, sslPolicyErrors) => true;

            if (Properties.Settings.Default.UsePAM)
            {
                apiKey = PAMService.GetApiKey();
            } else
            {
                apiKey = Properties.Settings.Default.ApiKey;
            }
        }

        public bool Import(Organisation organisation)
        {
            try
            {
                RestClient client = new RestClient(uri.Scheme + "://" + uri.Host + ":" + uri.Port);

                var request = new RestRequest(uri.AbsolutePath, Method.POST);

                if (!String.IsNullOrEmpty(domain))
                {
                    request.AddQueryParameter("domain", domain);
                }

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
