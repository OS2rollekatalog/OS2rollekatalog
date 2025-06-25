using System;
using RestSharp;
using System.Net;
using System.Collections.Generic;
using System.Configuration;
using ADSyncService.Email;
using System.Collections.Specialized;

namespace ADSyncService
{
    class RoleCatalogueStub
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static string apiKey = null;
        private static string baseUrl;
        private static string domain = Properties.Settings.Default.Domain;
        private EmailService emailService = EmailService.Instance;
        private RemoteConfigurationService remoteConfigurationService = RemoteConfigurationService.Instance;

        public RoleCatalogueStub()
        {
            ServicePointManager.ServerCertificateValidationCallback += (sender, cert, chain, error) => { return true; };

            if (Properties.Settings.Default.UsePAM)
            {
                apiKey = PAMService.GetApiKey();
            }
            else
            {
                apiKey = Properties.Settings.Default.ApiKey;
            }

            baseUrl = Properties.Settings.Default.ApiUrl;

            if (baseUrl.EndsWith("/"))
            {
                baseUrl = baseUrl.Substring(0, baseUrl.Length - 1);
            }
        }

        public OperationData GetOperationData()
        {
            try
            {
                RestClient client = new RestClient(baseUrl);

                string query = "";
                if (!String.IsNullOrEmpty(domain))
                {
                    query = $"?domain={domain}";
                }

                var request = new RestRequest("/api/ad/v2/operations" + query, Method.GET);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute<OperationData>(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("operation-data retrieved: " + result.Content);

                    return result.Data;
                }

                log.Error("OperationData call failed (" + result.StatusCode + ") : " + result.Content);
                emailService.EnqueueMail("OperationData call failed (" + result.StatusCode + ") : " + result.Content);
            }
            catch (Exception ex)
            {
                log.Error("OperationData call failed", ex);
                emailService.EnqueueMail("OperationData call failed", ex);
            }

            // return empty result to ensure sync job does nothing
            return new OperationData()
            {
                head = 0,
                operations = new List<Operation>()
            };
        }

        public List<string> GetUsersInItSystem(string itSystemId)
        {
            RestClient client = new RestClient(baseUrl);

            string query = "";
            if (!String.IsNullOrEmpty(domain))
            {
                query = $"?domain={domain}";
            }

            var request = new RestRequest("/api/itsystem/" + itSystemId + "/users" + query, Method.GET);
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;

            var result = client.Execute<List<string>>(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                log.Debug("itsystem members retrieved: " + result.Content);

                List<string> newSamAccountNames = new List<string>();
                if (result.Data != null)
                {
                    foreach (var samaccountname in result.Data)
                    {
                        if (!newSamAccountNames.Contains(samaccountname.ToLower()))
                        {
                            newSamAccountNames.Add(samaccountname.ToLower());
                        }
                    }
                }

                return newSamAccountNames;
            }

            log.Error("Read ItSystem members call failed (" + result.StatusCode + ") : " + result.Content);
            emailService.EnqueueMail("Read ItSystem members call failed (" + result.StatusCode + ") : " + result.Content);
            return null;
        }

        public List<string> GetUsersWithRole(string roleId)
        {
            RestClient client = new RestClient(baseUrl);

            string query = "";
            if (!String.IsNullOrEmpty(domain))
            {
                query = $"?domain={domain}";
            }

            var request = new RestRequest("/api/v2/userrole/" + roleId + "/users" + query, Method.GET);
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;

            var result = client.Execute<List<UserWithRole>>(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                log.Debug("userRole members retrieved: " + result.Content);

                List<string> newSamAccountNames = new List<string>();
                if (result.Data != null)
                {
                    foreach (var user in result.Data)
                    {
                        if (!newSamAccountNames.Contains(user.userId.ToLower()))
                        {
                            newSamAccountNames.Add(user.userId.ToLower());
                        }
                    }
                }

                return newSamAccountNames;
            }

            log.Error("Read UserRole members call failed (" + result.StatusCode + ") : " + result.Content);
            emailService.EnqueueMail("Read UserRole members call failed (" + result.StatusCode + ") : " + result.Content);
            return null;
        }

        public ItSystemData GetItSystemData(string itSystemId)
        {
            RestClient client = new RestClient(baseUrl);

            var request = new RestRequest("/api/itsystem/manage/" + itSystemId, Method.GET);
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;

            var result = client.Execute<ItSystemData>(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                log.Debug("itsystem data retrieved: " + result.Content);

                return result.Data;
            }

            log.Error("Read ItSystem Data call failed (" + result.StatusCode + ") : " + result.Content);
            emailService.EnqueueMail("Read ItSystem Data call failed (" + result.StatusCode + ") : " + result.Content);
            return null;
        }

        public void SetItSystemData(string itSystemId, ItSystemData itSystemData)
        {
            RestClient client = new RestClient(baseUrl);

            string query = "";
            if (!string.IsNullOrEmpty(domain))
            {
                query = $"?domain={domain}";
            }

            if (ReImportUsersEnabled())
            {
                query += (query.Length > 0) ? "&updateUserAssignments=true" : "?updateUserAssignments=true";
            }
            var request = new RestRequest("/api/itsystem/manage/" + itSystemId + query, Method.POST);
            request.RequestFormat = DataFormat.Json;
            request.AddHeader("Content-Type", "application/json");
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;
            request.AddJsonBody(itSystemData);

            var result = client.Execute(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                return;
            }

            log.Error("Set ItSystem Data call failed (" + result.StatusCode + ") : " + result.Content);
            emailService.EnqueueMail("Set ItSystem Data call failed (" + result.StatusCode + ") : " + result.Content);
        }

        public SyncData GetSyncData(bool fullSync)
        {
            try
            {
                RestClient client = new RestClient(baseUrl);
                NameValueCollection queryString = System.Web.HttpUtility.ParseQueryString(string.Empty);

                if (!string.IsNullOrEmpty(domain))
                {
                    queryString.Add("domain", domain);
                }

                if (fullSync) {
                    queryString.Add("fullsync", "true");
                }

                var request = new RestRequest("/api/ad/v2/sync" + "?" + queryString.ToString(), Method.GET);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute<SyncData>(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("sync-data retrieved: " + result.Content);

                    // lower-case the samaccountnames for easy comparison
                    var syncdata = result.Data;
                    foreach (var assignment in syncdata.assignments)
                    {
                        List<string> newSamAccountNames = new List<string>();

                        foreach (var samaccountname in assignment.samaccountNames)
                        {
                            if (!newSamAccountNames.Contains(samaccountname.ToLower()))
                            {
                                newSamAccountNames.Add(samaccountname.ToLower());
                            }
                        }

                        assignment.samaccountNames = newSamAccountNames;
                    }

                    return syncdata;
                }

                log.Error("Sync call failed (" + result.StatusCode + ") : " + result.Content);
                emailService.EnqueueMail("Sync call failed (" + result.StatusCode + ") : " + result.Content);
            }
            catch (Exception ex)
            {
                log.Error("Sync call failed", ex);
                emailService.EnqueueMail("Sync call failed", ex);
            }

            // return empty result to ensure sync job does nothing
            return new SyncData()
            {
                head = 0,
                assignments = new List<Assignment>()
            };
        }

        public void ResetHead(long head, long maxHead)
        {
            try
            {
                RestClient client = new RestClient(baseUrl);
                string query = "?maxHead=" + maxHead;
                if (!String.IsNullOrEmpty(domain))
                {
                    query += $"&domain={domain}";
                }

                var request = new RestRequest("/api/ad/v2/sync/" + head + query, Method.DELETE);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("reset head succeeded!");
                }
                else
                {
                    log.Error("Reset call failed (" + result.StatusCode + ") : " + result.Content);
                    emailService.EnqueueMail("Reset call failed (" + result.StatusCode + ") : " + result.Content);
                }
            }
            catch (Exception ex)
            {
                log.Error("Reset call failed", ex);
                emailService.EnqueueMail("Reset call failed", ex);
            }
        }

        public void ResetOperationHead(long head)
        {
            try
            {
                RestClient client = new RestClient(baseUrl);
                string query = "";
                if (!String.IsNullOrEmpty(domain))
                {
                    query = $"?domain={domain}";
                }

                var request = new RestRequest("/api/ad/v2/operations/" + head + query, Method.DELETE);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("reset operation head succeeded!");
                }
                else
                {
                    log.Error("Reset operation call failed (" + result.StatusCode + ") : " + result.Content);
                    emailService.EnqueueMail("Reset operation call failed (" + result.StatusCode + ") : " + result.Content);
                }
            }
            catch (Exception ex)
            {
                log.Error("Reset operation call failed", ex);
                emailService.EnqueueMail("Reset operation call failed", ex);
            }
        }

        public bool GetShouldUploadLog()
        {
            RestClient client = new RestClient(baseUrl);

            var request = new RestRequest("/api/uploadLog", Method.GET);
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;

            var result = client.Execute<string>(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                if (result.Data != null)
                {
                    return Boolean.Parse(result.Data);
                }
            }

            return false;
        }

        public RemoteConfiguration GetConfiguration()
        {
            try
            {
                RestClient client = new RestClient(baseUrl);

                var request = new RestRequest("/api/v2/ad/getConfiguration/" + domain, Method.GET);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute<RemoteConfiguration>(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("ConfigurationFromRC-data retrieved: " + result.Content);
                    return result.Data;
                } else if (result.StatusCode.Equals(HttpStatusCode.NoContent))
                {
                    SetConfigurationInRC();
                    return GetConfiguration();
                } else if (result.StatusCode.Equals(HttpStatusCode.NotFound))
                {
                    return null;
                }

                log.Error("Get configuration from RC call failed (" + result.StatusCode + ") : " + result.Content);
                emailService.EnqueueMail("Get configuration from RC call failed (" + result.StatusCode + ") : " + result.Content);
            }
            catch (Exception ex)
            {
                log.Error("Get configuration from RC call failed", ex);
                emailService.EnqueueMail("Get configuration from RC call failed", ex);
            }

            return null;
        }

        public void SetConfigurationInRC()
        {
            RestClient client = new RestClient(baseUrl);
            var request = new RestRequest("/api/v2/ad/writeConfiguration/" + domain, Method.POST);
            request.RequestFormat = DataFormat.Json;
            request.AddHeader("Content-Type", "application/json");
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;
            request.AddJsonBody(remoteConfigurationService.GetLocalConfiguration());

            var result = client.Execute(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                return;
            }

            log.Error("SetConfigurationInRC call failed (" + result.StatusCode + ") : " + result.Content);
            emailService.EnqueueMail("SetConfigurationInRC call failed (" + result.StatusCode + ") : " + result.Content);
        }

        public void SendConfigurationError(string errorMsg)
        {
            RestClient client = new RestClient(baseUrl);
            var request = new RestRequest("/api/v2/ad/error/" + domain, Method.POST);
            request.RequestFormat = DataFormat.Json;
            request.AddHeader("Content-Type", "application/json");
            request.AddHeader("ApiKey", apiKey);
            request.AddParameter("text/plain", errorMsg, ParameterType.RequestBody);

            var result = client.Execute(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                return;
            }

            log.Error("SendConfigurationError call failed (" + result.StatusCode + ") : " + result.Content);
            emailService.EnqueueMail("SendConfigurationError call failed (" + result.StatusCode + ") : " + result.Content);
        }

        private static bool ReImportUsersEnabled()
        {
            string reImportUsers = ConfigurationManager.AppSettings["ReImportUsers"];
            return reImportUsers != null && reImportUsers.Equals("Yes");
        }
    }
}
