--liquibase formatted sql
--changeset insights-api:14817_enable_http_caching_for_tiles splitStatements:false stripComments:false endDelimiter:; runOnChange:true

drop table if exists insights_metadata;

create table insights_metadata
(
    data_last_update_time timestamp
);

insert into insights_metadata(data_last_update_time)
values ('2023-03-24 12:00:00');