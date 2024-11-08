alter table history_it_systems
    alter column it_system_name nvarchar(256) not null
    go

alter table it_system_updates
    alter column it_system_name nvarchar(256) null
    go