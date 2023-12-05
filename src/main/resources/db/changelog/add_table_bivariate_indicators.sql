--liquibase formatted sql
--changeset insights-api:add_table_bivariate_indicators splitStatements:false stripComments:false endDelimiter:; runOnChange:true
drop table if exists bivariate_indicators;
create table bivariate_indicators (
    param_id text,
    param_label text,
    copyrights json,
    direction json,
    is_base boolean default false not null,
    description text,
    coverage text,
    update_frequency text,
    is_public boolean,
    application json,
    unit_id text
);
