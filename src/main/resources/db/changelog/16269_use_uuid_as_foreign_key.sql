--liquibase formatted sql
--changeset insights-api:16269_use_uuid_as_foreign_key.sql splitStatements:false stripComments:false endDelimiter:; runOnChange:true

drop table if exists bivariate_axis_test; --as bivariate_axis_v2 table has been created
drop table if exists bivariate_axis_stats; --as bivariate_axis table is being used
drop table if exists bivariate_axis_stats_v2; --as bivariate_axis_v2 table is being used

alter table bivariate_axis_v2
    add column if not exists numerator_uuid   uuid,
    drop constraint if exists fk_bivariate_axis_v2_numerator_uuid,
    add constraint fk_bivariate_axis_v2_numerator_uuid foreign key (numerator_uuid)
        references bivariate_indicators_metadata (param_uuid) on delete cascade,
    add column if not exists denominator_uuid uuid,
    drop constraint if exists fk_bivariate_axis_v2_denominator_uuid,
    add constraint fk_bivariate_axis_v2_denominator_uuid foreign key (denominator_uuid)
        references bivariate_indicators_metadata (param_uuid) on delete cascade;

alter table bivariate_axis_correlation_v2
    add column if not exists x_num_uuid uuid,
    drop constraint if exists fk_bivariate_axis_correlation_v2_x_num_uuid,
    add constraint fk_bivariate_axis_correlation_v2_x_num_uuid foreign key (x_num_uuid)
        references bivariate_indicators_metadata (param_uuid) on delete cascade,
    add column if not exists x_den_uuid uuid,
    drop constraint if exists fk_bivariate_axis_correlation_v2_x_den_uuid,
    add constraint fk_bivariate_axis_correlation_v2_x_den_uuid foreign key (x_den_uuid)
        references bivariate_indicators_metadata (param_uuid) on delete cascade,
    add column if not exists y_num_uuid uuid,
    drop constraint if exists fk_bivariate_axis_correlation_v2_y_num_uuid,
    add constraint fk_bivariate_axis_correlation_v2_y_num_uuid foreign key (y_num_uuid)
        references bivariate_indicators_metadata (param_uuid) on delete cascade,
    add column if not exists y_den_uuid uuid,
    drop constraint if exists fk_bivariate_axis_correlation_v2_y_den_uuid,
    add constraint fk_bivariate_axis_correlation_v2_y_den_uuid foreign key (y_den_uuid)
        references bivariate_indicators_metadata (param_uuid) on delete cascade;