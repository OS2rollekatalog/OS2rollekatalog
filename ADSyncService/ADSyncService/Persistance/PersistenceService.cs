using ADSyncService.Util;
using Microsoft.Data.Sqlite;
using System;
using System.Collections.Concurrent;
using System.IO;
using System.Reflection;
using System.Threading;

namespace ADSyncService.Persistance
{
    public class PersistenceService
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        readonly ConcurrentDictionary<string, DateTime> dictionary = new ConcurrentDictionary<string, DateTime>(StringComparer.OrdinalIgnoreCase);

        public PersistenceService()
        {
            log.Debug("Initializing sqlite connection manager");
            SqliteConnectionManager.Initialize();
            InitDatabase();

            Read();
        }

        private void InitDatabase()
        {
            log.Debug("Begin - Create groups cache database");
            using (var sqliteConnection = SqliteConnectionManager.Instance.GetOpenConnection())
            {
                var createTableCmd = sqliteConnection.CreateCommand();
                createTableCmd.CommandText = "create table if not exists group_member_cache(group_dn varchar(255) primary key, last_updated datetime)";
                createTableCmd.ExecuteNonQuery();
                sqliteConnection.Close();
            }
            log.Debug("End - Create groups cache database");
        }

        internal DateTime? Get(string groupDN)
        {
            if (dictionary.ContainsKey(groupDN))
            {
                return dictionary[groupDN];
            } else
            {
                return null;
            }
        }

        internal void Update(string groupDN, DateTime lastUpdated)
        {
            dictionary[groupDN] = lastUpdated;
        }

        internal void Read()
        {
            log.Debug("Begin - Read group caches from DB");
            using (var sqliteConnection = SqliteConnectionManager.Instance.GetOpenConnection())
            {
                using (var selectCmd = sqliteConnection.CreateCommand())
                {
                    selectCmd.CommandText = @"select group_dn, last_updated from group_member_cache";
                    var r = selectCmd.ExecuteReader();
                    while (r.Read())
                    {
                        string groupDn = (string)r["group_dn"];
                        DateTime lastUpdated = Convert.ToDateTime(r["last_updated"]);
                        
                        dictionary[groupDn] = lastUpdated;
                    }
                    
                    sqliteConnection.Close();
                }
            }
            log.Debug("End - Read group caches from DB");
        }

        internal void Save()
        {
            log.Debug("Begin - Saving group cache to DB");
            using (var sqliteConnection = SqliteConnectionManager.Instance.GetOpenConnection())
            {
                sqliteConnection.Open();
                var transaction = sqliteConnection.BeginTransaction();
                foreach (var groupDn in dictionary.Keys)
                {
                    var insertCmd = sqliteConnection.CreateCommand();
                    insertCmd.CommandText = "insert into group_member_cache (group_dn,last_updated) values (@groupDN,@lastUpdated) on conflict(group_dn) do update set last_updated=excluded.last_updated;";
                    insertCmd.Parameters.Add(new SqliteParameter("@groupDN", groupDn));
                    insertCmd.Parameters.Add(new SqliteParameter("@lastUpdated", dictionary[groupDn]));
                    insertCmd.ExecuteNonQuery();
                }
                transaction.Commit();
                sqliteConnection.Close();
            }
            log.Debug("End - Saving group cache to DB");
        }
    }
}
