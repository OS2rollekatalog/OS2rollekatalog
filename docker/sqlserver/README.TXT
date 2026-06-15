This is a SQL Server docker image. For testing our flyway scripts on SQL Server, simply start this image,
and change the application.properties configuration to use the SQL Server database connection instead of MySQL

After startup, find the pid of the container and run this against the pid

docker exec -it <docker-container-id> /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U SA -P 'Test1234' -Q 'CREATE DATABASE rc'
