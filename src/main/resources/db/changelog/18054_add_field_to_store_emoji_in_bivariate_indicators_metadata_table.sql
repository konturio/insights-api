--liquibase formatted sql
--changeset insights-api:18054_add_field_to_store_emoji_in_bivariate_indicators_metadata_table.sql endDelimiter:; runOnChange:true

ALTER TABLE bivariate_indicators_metadata ADD COLUMN emoji text;