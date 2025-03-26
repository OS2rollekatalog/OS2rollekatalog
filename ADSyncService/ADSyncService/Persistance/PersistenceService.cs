using Microsoft.Data.Sqlite;
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using static System.Net.WebRequestMethods;

namespace ADSyncService.Persistance
{
    public class PersistenceService
    {
        readonly ConcurrentDictionary<string, DateTime> dictionary = new ConcurrentDictionary<string, DateTime>(StringComparer.OrdinalIgnoreCase);
        private SqliteConnection sqliteConnection;

        public PersistenceService()
        {
            var persistenceFolder = Path.Combine(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location), "persistence");
            Directory.CreateDirectory(persistenceFolder);
            var persistencePath = Path.Combine(persistenceFolder, "persistence.db");
            sqliteConnection = new SqliteConnection($"Data Source={persistencePath}");
            InitDatabase();
            Read();
        }

        private void InitDatabase()
        {
            sqliteConnection.Open();
            var createTableCmd = sqliteConnection.CreateCommand();
            createTableCmd.CommandText = "create table if not exists group_member_cache(group_dn varchar(255) primary key, last_updated datetime)";
            createTableCmd.ExecuteNonQuery();
            sqliteConnection.Close();
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
            sqliteConnection.Open();
            using (var selectCmd = sqliteConnection.CreateCommand())
            {
                selectCmd.CommandText = @"select group_dn, last_updated from group_member_cache";
                var r = selectCmd.ExecuteReader();
                while (r.Read())
                {
                    string groupDN = (string)r["group_dn"];
                    DateTime lastUpdated = Convert.ToDateTime(r["last_updated"]);

                    dictionary[groupDN] = lastUpdated;
                }

                sqliteConnection.Close();
            }
        }

        internal void Save()
        {
            sqliteConnection.Open();
            var transaction = sqliteConnection.BeginTransaction();
            foreach (var groupDN in dictionary.Keys)
            {
                var insertCmd = sqliteConnection.CreateCommand();
                insertCmd.CommandText = "insert into group_member_cache (group_dn,last_updated) values (@groupDN,@lastUpdated) on conflict(group_dn) do update set last_updated=excluded.last_updated;";
                insertCmd.Parameters.Add(new SqliteParameter("@groupDN", groupDN));
                insertCmd.Parameters.Add(new SqliteParameter("@lastUpdated", dictionary[groupDN]));
                insertCmd.ExecuteNonQuery();
            }
            transaction.Commit();
            sqliteConnection.Close();
        }
    }
}
