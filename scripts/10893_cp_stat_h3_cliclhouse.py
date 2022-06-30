import time
from contextlib import closing

import psycopg2
from clickhouse_driver import connect

page_size = 100000
page_offset = 0
time_sleep_sec = 1
with closing(psycopg2.connect(dbname='insights-api', user='user',
                              password='password', host='localhost', port=55432)) as conn_pg:
    with conn_pg.cursor() as cursor_pg:
        cursor_pg.execute("""select id, h3,
        area_km2,
        populated_area_km2,
        population,
        count,
        building_count,
        highway_length,
        resolution,
        zoom,
        one,
        total_building_count,
        count_6_months,
        building_count_6_months,
        highway_length_6_months,
        osm_users,
        residential,
        gdp,
        min_ts,
        max_ts,
        avgmax_ts,
        local_hours,
        total_hours,
        view_count,
        wildfires,
        covid19_vaccines,
        covid19_confirmed,
        population_prev,
        industrial_area,
        volcanos_count,
        pop_under_5_total,
        pop_over_65_total,
        poverty_families_total,
        pop_disability_total,
        pop_not_well_eng_speak,
        pop_without_car,
        man_distance_to_fire_brigade,
        man_distance_to_hospital,
        total_road_length,
        foursquare_places_count,
        foursquare_visits_count,
        view_count_bf2402,
        avg_slope,
        avg_elevation,
        forest,
        evergreen_needle_leaved_forest,
        shrubs,
        herbage,
        unknown_forest,
        avg_ndvi,
        days_maxtemp_over_32c_1c,
        days_maxtemp_over_32c_2c,
        days_mintemp_above_25c_1c,
        days_mintemp_above_25c_2c,
        days_maxwetbulb_over_32c_1c,
        days_maxwetbulb_over_32c_2c,
        mandays_maxtemp_over_32c_1c,
        mhr_index,
        mhe_index,
        resilience_index,
        coping_capacity_index,
        vulnerability_index,
        hazardous_days_count,
        earthquake_days_count,
        wildfire_days_count,
        drought_days_count,
        cyclone_days_count,
        volcano_days_count,
        flood_days_count
        from stat_h3_clickhouse order by id limit {} offset {}""".format(page_size, page_offset))
        rows_pg = cursor_pg.fetchall()
        with closing(connect('clickhouse://localhost:9000')) as conn_click:
            with conn_click.cursor() as cursor_click:
                page_offset = page_offset + page_size
                rows_pg_len = len(rows_pg)
                while rows_pg_len > 0:
                    cursor_click.executemany(
                        'INSERT INTO stat_h3_indicators (id, h3, area_km2, populated_area_km2, population, '
                        'count, building_count, highway_length, resolution, zoom, one, total_building_count, '
                        'count_6_months, building_count_6_months, highway_length_6_months, osm_users, residential, '
                        'gdp, min_ts, max_ts, avgmax_ts, local_hours, total_hours, view_count, wildfires, '
                        'covid19_vaccines, covid19_confirmed, population_prev, industrial_area, volcanos_count, '
                        'pop_under_5_total, pop_over_65_total, poverty_families_total, pop_disability_total, '
                        'pop_not_well_eng_speak, pop_without_car, man_distance_to_fire_brigade, '
                        'man_distance_to_hospital, total_road_length, foursquare_places_count, '
                        'foursquare_visits_count, view_count_bf2402, avg_slope, avg_elevation, '
                        'forest, evergreen_needle_leaved_forest, shrubs, herbage, unknown_forest, '
                        'avg_ndvi, days_maxtemp_over_32c_1c, days_maxtemp_over_32c_2c, '
                        'days_mintemp_above_25c_1c, days_mintemp_above_25c_2c, '
                        'days_maxwetbulb_over_32c_1c, days_maxwetbulb_over_32c_2c, '
                        'mandays_maxtemp_over_32c_1c, mhr_index, mhe_index, resilience_index, '
                        'coping_capacity_index, vulnerability_index, hazardous_days_count, '
                        'earthquake_days_count, wildfire_days_count, drought_days_count, cyclone_days_count, '
                        'volcano_days_count, flood_days_count) VALUES',
                        rows_pg)
                    print('Page offset: {}'.format(page_offset))
                    time.sleep(time_sleep_sec)
                    cursor_pg.execute("""select id, h3,
                        area_km2,
                        populated_area_km2,
                        population,
                        count,
                        building_count,
                        highway_length,
                        resolution,
                        zoom,
                        one,
                        total_building_count,
                        count_6_months,
                        building_count_6_months,
                        highway_length_6_months,
                        osm_users,
                        residential,
                        gdp,
                        min_ts,
                        max_ts,
                        avgmax_ts,
                        local_hours,
                        total_hours,
                        view_count,
                        wildfires,
                        covid19_vaccines,
                        covid19_confirmed,
                        population_prev,
                        industrial_area,
                        volcanos_count,
                        pop_under_5_total,
                        pop_over_65_total,
                        poverty_families_total,
                        pop_disability_total,
                        pop_not_well_eng_speak,
                        pop_without_car,
                        man_distance_to_fire_brigade,
                        man_distance_to_hospital,
                        total_road_length,
                        foursquare_places_count,
                        foursquare_visits_count,
                        view_count_bf2402,
                        avg_slope,
                        avg_elevation,
                        forest,
                        evergreen_needle_leaved_forest,
                        shrubs,
                        herbage,
                        unknown_forest,
                        avg_ndvi,
                        days_maxtemp_over_32c_1c,
                        days_maxtemp_over_32c_2c,
                        days_mintemp_above_25c_1c,
                        days_mintemp_above_25c_2c,
                        days_maxwetbulb_over_32c_1c,
                        days_maxwetbulb_over_32c_2c,
                        mandays_maxtemp_over_32c_1c,
                        mhr_index,
                        mhe_index,
                        resilience_index,
                        coping_capacity_index,
                        vulnerability_index,
                        hazardous_days_count,
                        earthquake_days_count,
                        wildfire_days_count,
                        drought_days_count,
                        cyclone_days_count,
                        volcano_days_count,
                        flood_days_count
                        from stat_h3_clickhouse order by id limit {} offset {}""".format(page_size, page_offset))
                    rows_pg = cursor_pg.fetchall()
                    page_offset = page_offset + page_size
                    rows_pg_len = len(rows_pg)
