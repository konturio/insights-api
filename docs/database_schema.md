# Database Schema Overview

The project uses PostgreSQL with PostGIS and H3 extensions. Database migrations are managed via Liquibase SQL changelogs located in `src/main/resources/db/changelog`.

Below is a summary of the main tables defined in `init_schema.sql` and related changelog files. Only the most significant columns are listed for brevity.

## bivariate_axis_correlation_v2
- `correlation` – correlation value
- `quality` – data quality value
- `x_numerator_id`, `x_denominator_id` – references to numerator and denominator indicators (UUID)
- `y_numerator_id`, `y_denominator_id` – references to numerator and denominator indicators (UUID)

## bivariate_axis_overrides
Stores user-provided overrides for bivariate axis labels and values.
- `numerator_id` (UUID, not null)
- `denominator_id` (UUID, not null)
- `label`, `min`, `p25`, `p75`, `max`
- `min_label`, `p25_label`, `p75_label`, `max_label`

## bivariate_axis_v2
Calculated statistics for a pair of indicators.
- `numerator` and `denominator` (text)
- various min/median/max/mean values and quality fields
- `numerator_uuid`, `denominator_uuid` (UUID references)
- `default_transform`, `transformations` (JSON)

## bivariate_colors
Defines preset colors used to draw bivariate matrices.
- `color` (hex string)
- `color_comment`
- `corner` (JSON describing color meaning)

## bivariate_indicators_metadata
Metadata for uploaded indicators.
- `param_id` – internal id used in queries
- `param_label` – human readable label
- `internal_id` – UUID generated for the record
- `external_id` – UUID returned to clients
- `owner`, `state`, `is_public`, `allowed_users` and other attributes
- `unit_id` – reference to `bivariate_unit`
- `coverage_polygon` (added in later changelog)

## bivariate_unit / bivariate_unit_localization
Contains information about measurement units and their localized names.

## llm_cache, nominatim_cache, search_history
Auxiliary tables used for caching AI responses, geocoding results and user search history.

## shedlock
Table used by [ShedLock](https://github.com/lukas-krecan/ShedLock) to manage distributed scheduler locks.

## stat_h3_geom
Mapping between H3 index and geometry used for tile generation.

## stat_h3_transposed
Stores indicator values per H3 cell. The table is partitioned by `indicator_uuid` into multiple `stat_h3_transposed_p*` tables for better performance.

## Additional Functions and Changes
Other changelog files create helper SQL functions (`calculate_area_resolution`, `zoom_to_h3_resolution`) and additional tables such as `bivariate_unit`, `bivariate_unit_localization` and `bivariate_colors` with seed data.

