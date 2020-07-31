using System;
using System.Collections.Generic;
using System.Net;
using System.Text;
using RestSharp;

namespace RoleCatalogueAttributeStore
{
    class RoleCatalogueStub
    {
        static RoleCatalogueStub()
        {
            // enable TLS 1.2 support for .NET 4.5
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
        }

        public static string GetNameID(string userid)
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;

            userid = GetUserId(userid);
            var client = new RestClient();

            var url = Configuration.GetInstance().RoleCatalogueUrl;
            if (!url.EndsWith("/"))
            {
                url = url + "/";
            }

            client.BaseUrl = new Uri(url + "api/user/" + userid + "/nameid");

            var request = new RestRequest();
            request.AddHeader("ApiKey", Configuration.GetInstance().ApiKey);

            IRestResponse<UserResponse> response = client.Execute<UserResponse>(request);

            if (!response.StatusCode.Equals(System.Net.HttpStatusCode.OK))
            {
                AttributeStoreLogger.Warn("Failed to find '" + userid + "' in RoleCatalogue. Status from RoleCatalogue was: " + response.StatusCode.ToString());
                response.Data = new UserResponse()
                {
                    nameID="Unknown User"
                };
            }

            return response.Data.nameID;
        }

        public static string GetOIOBPP(string userid, string itsystem)
        {
            userid = GetUserId(userid);
            var client = new RestClient();

            var url = Configuration.GetInstance().RoleCatalogueUrl;
            if (!url.EndsWith("/"))
            {
                url = url + "/";
            }

            client.BaseUrl = new Uri(url + "api/user/" + userid + "/roles?system=" + itsystem);

            var request = new RestRequest();
            request.AddHeader("ApiKey", Configuration.GetInstance().ApiKey);

            IRestResponse<UserResponse> response = client.Execute<UserResponse>(request);

            if (!response.StatusCode.Equals(System.Net.HttpStatusCode.OK))
            {
                AttributeStoreLogger.Warn("Failed to find '" + userid + "' in RoleCatalogue. Status from RoleCatalogue was: " + response.StatusCode.ToString());
                response.Data = new UserResponse()
                {
                    oioBPP = "",
                    nameID = ""
                };
            }
            else
            {
                StringBuilder builder = new StringBuilder();
                builder.Append("Roles issued\n");
                builder.Append("Timestamp: " + DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss") + "\n");
                builder.Append("User: " + response.Data.nameID + "\n");
                builder.Append("OIO-BPP: " + response.Data.oioBPP + "\n");
                builder.Append("RoleMap: " + RoleMapToString(response.Data.roleMap));
                AttributeStoreLogger.Info(builder.ToString());
            }

            return response.Data.oioBPP;
        }

        private static string RoleMapToString(Dictionary<string, string> map)
        {
            StringBuilder builder = new StringBuilder();

            foreach (string key in map.Keys)
            {
                if (builder.Length > 0)
                {
                    builder.Append(",");
                }

                builder.Append(key + "=" + map[key]);
            }

            return builder.ToString();
        }

        public static string[][] GetUserRoles(string userid, string itsystem)
        {
            UserResponse response = GetRoles(userid, itsystem);

            // default empty result
            string[][] result = new string[0][];

            if (response.userRoles != null && response.userRoles.ToArray().Length > 0)
            {
                string[] userRoles = response.userRoles.ToArray();

                result = new string[userRoles.Length][];
                for (int i = 0; i < userRoles.Length; i++)
                {
                    result[i] = new string[1] { userRoles[i] };
                }
            }

            if (response.userRoles != null)
            {
                StringBuilder builder = new StringBuilder();
                builder.Append("Roles issued\n");
                builder.Append("Timestamp: " + DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss") + "\n");
                builder.Append("User: " + response.nameID + "\n");
                builder.Append("RoleMap: " + RoleMapToString(response.roleMap));
                AttributeStoreLogger.Info(builder.ToString());
            }

            return result;
        }

        internal static string[][] GetFunctionRoles(string userid, string itsystem)
        {
            UserResponse response = GetRoles(userid, itsystem);

            // default empty result
            string[][] result = new string[0][];

            if (response.functionRoles != null && response.functionRoles.ToArray().Length > 0)
            {
                string[] functionRoles = response.functionRoles.ToArray();

                result = new string[functionRoles.Length][];
                for (int i = 0; i < functionRoles.Length; i++)
                {
                    result[i] = new string[1] { functionRoles[i] };
                }
            }

            if (response.functionRoles != null)
            {
                StringBuilder builder = new StringBuilder();
                builder.Append("Roles issued\n");
                builder.Append("Timestamp: " + DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss") + "\n");
                builder.Append("User: " + response.nameID + "\n");
                builder.Append("RoleMap: " + RoleMapToString(response.roleMap));
                AttributeStoreLogger.Info(builder.ToString());
            }

            return result;
        }

        internal static string[][] GetDataRoles(string userid, string itsystem)
        {
            UserResponse response = GetRoles(userid, itsystem);

            // default empty result
            string[][] result = new string[0][];

            if (response.dataRoles != null && response.dataRoles.ToArray().Length > 0)
            {
                string[] dataRoles = response.dataRoles.ToArray();

                result = new string[dataRoles.Length][];
                for (int i = 0; i < dataRoles.Length; i++)
                {
                    result[i] = new string[1] { dataRoles[i] };
                }
            }
            
            if (response.dataRoles != null)
            {
                StringBuilder builder = new StringBuilder();
                builder.Append("Roles issued\n");
                builder.Append("Timestamp: " + DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss") + "\n");
                builder.Append("User: " + response.nameID + "\n");
                builder.Append("RoleMap: " + RoleMapToString(response.roleMap));
                AttributeStoreLogger.Info(builder.ToString());
            }

            return result;
        }

        internal static string[][] VerifySingleUserRole(string userId, string roleId, string itsystem)
        {
            bool foundRole = CheckUserRole(userId, roleId, itsystem);

            // default empty result
            string[][] result = new string[0][];

            if (foundRole)
            {
                result = new string[1][];
                result[0] = new string[1] { "found" };
            }

            return result;
        }

        internal static string[][] VerifySingleSystemRole(string userId, string roleId, string itsystem)
        {
            bool foundRole = CheckSystemRole(userId, roleId, itsystem);

            // default empty result
            string[][] result = new string[0][];

            if (foundRole)
            {
                result = new string[1][];
                result[0] = new string[1] { "found" };
            }

            return result;
        }

        public static string[][] GetSystemRoles(string userid, string itsystem)
        {
            UserResponse response = GetRoles(userid, itsystem);

            // default empty result
            string[][] result = new string[0][];

            if (response.systemRoles != null && response.systemRoles.ToArray().Length > 0)
            {
                string[] systemRoles = response.systemRoles.ToArray();

                result = new string[systemRoles.Length][];
                for (int i = 0; i < systemRoles.Length; i++)
                {
                    result[i] = new string[1] { systemRoles[i] };
                }

            }

            return result;
        }

        private static string GetUserId(string userid)
        {
            if (userid.Contains("\\"))
            {
                int idx = userid.LastIndexOf("\\");
                userid = userid.Substring(idx + 1);
            }

            return userid;
        }

        private static UserResponse GetRoles(string userid, string itsystem)
        {
            userid = GetUserId(userid);
            var client = new RestClient();

            var url = Configuration.GetInstance().RoleCatalogueUrl;
            if (!url.EndsWith("/"))
            {
                url = url + "/";
            }

            client.BaseUrl = new Uri(url + "api/user/" + userid + "/rolesAsList?system=" + itsystem);

            var request = new RestRequest();
            request.AddHeader("ApiKey", Configuration.GetInstance().ApiKey);

            IRestResponse<UserResponse> response = client.Execute<UserResponse>(request);

            if (!response.StatusCode.Equals(System.Net.HttpStatusCode.OK))
            {
                AttributeStoreLogger.Warn("Failed to find '" + userid + "' in RoleCatalogue. Status from RoleCatalogue was: " + response.StatusCode.ToString());
                response.Data = new UserResponse()
                {
                     nameID = ""
                };
            }
            else
            {
                StringBuilder builder = new StringBuilder();
                builder.Append("Roles issued\n");
                builder.Append("Timestamp: " + DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss") + "\n");
                builder.Append("User: " + response.Data.nameID + "\n");
                builder.Append("RoleMap: " + RoleMapToString(response.Data.roleMap));
                AttributeStoreLogger.Info(builder.ToString());
            }

            return response.Data;
        }

        private static bool CheckUserRole(string userid, string roleId, string itsystem)
        {
            userid = GetUserId(userid);
            var client = new RestClient();

            var url = Configuration.GetInstance().RoleCatalogueUrl;
            if (!url.EndsWith("/"))
            {
                url = url + "/";
            }

            client.BaseUrl = new Uri(url + "api/user/" + userid + "/hasUserRole/" + roleId + "?system=" + itsystem);

            var request = new RestRequest();
            request.AddHeader("ApiKey", Configuration.GetInstance().ApiKey);

            IRestResponse<UserResponse> response = client.Execute<UserResponse>(request);

            if (response.StatusCode.Equals(System.Net.HttpStatusCode.OK))
            {
                return true;
            }
            else if (!response.StatusCode.Equals(System.Net.HttpStatusCode.NotFound))
            {
                AttributeStoreLogger.Warn("Failed to lookup roles for '" + userid + "' in RoleCatalogue. Status from RoleCatalogue was: " + response.StatusCode.ToString());
            }

            return false;
        }

        private static bool CheckSystemRole(string userid, string roleId, string itsystem)
        {
            userid = GetUserId(userid);
            var client = new RestClient();

            var url = Configuration.GetInstance().RoleCatalogueUrl;
            if (!url.EndsWith("/"))
            {
                url = url + "/";
            }

            client.BaseUrl = new Uri(url + "api/user/" + userid + "/hasSystemRole/?roleIdentifier=" + Uri.EscapeDataString(roleId) + "&system=" + itsystem);

            var request = new RestRequest();
            request.AddHeader("ApiKey", Configuration.GetInstance().ApiKey);

            IRestResponse<UserResponse> response = client.Execute<UserResponse>(request);

            if (response.StatusCode.Equals(System.Net.HttpStatusCode.OK))
            {
                return true;
            }
            else if (!response.StatusCode.Equals(System.Net.HttpStatusCode.NotFound))
            {
                AttributeStoreLogger.Warn("Failed to lookup roles for '" + userid + "' in RoleCatalogue. Status from RoleCatalogue was: " + response.StatusCode.ToString());
            }

            return false;
        }
    }
}
