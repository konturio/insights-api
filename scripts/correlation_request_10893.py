from contextlib import closing
from datetime import datetime

import psycopg2
from clickhouse_driver import connect


def make_request(input_geom, indx):
    with closing(psycopg2.connect(dbname='insights-api', user='insights-api',
                                  password='c3RlUbuRM4RUSP4w0cEfyGUPAN5', host='localhost', port=55432)) as conn_pg:
        with conn_pg.cursor() as cursor_pg:
            cursor_pg.execute("""
        with validated_input as (
    select calculate_validated_input('{}') geom
)
select distinct on (h.h3) h.h3
from (
         select ST_Subdivide(v.geom, 30) geom
         from validated_input v
     ) p
         cross join
     lateral (
         select h3
         from stat_h3 sh
         where ST_Intersects(sh.geom, p.geom)
         order by h3
         ) h
        """.format(input_geom))
            rows_pg = cursor_pg.fetchall()
            print('Thread: %s Data from postgres executed' % indx)
            with closing(connect('clickhouse://localhost:9000')) as conn_click:
                with conn_click.cursor() as cursor_click:
                    cursor_click.execute('CREATE TEMPORARY TABLE h3_id%s (h3 String)' % indx)
                    cursor_click.fetchall()
                    print('Thread: %s Temporary table created' % indx)
                    cursor_click.executemany('INSERT INTO h3_id%s (h3) VALUES' % indx, rows_pg)
                    print('Thread: %s Insert data into temporary table' % indx)
                    start = datetime.now()
                    cursor_click.execute("""select corrIf(population_prev / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(population_prev / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(wildfires / area_km2, osm_users / area_km2, (area_km2 != 0 and area_km2 != 0)),
	   corrIf(wildfires / total_road_length, osm_users / area_km2, (total_road_length != 0 and area_km2 != 0)),
	   corrIf(wildfires / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(wildfires / populated_area_km2, osm_users / area_km2, (populated_area_km2 != 0 and area_km2 != 0)),
	   corrIf(wildfires / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(cyclone_days_count / one, osm_users / area_km2, (one != 0 and area_km2 != 0)),
	   corrIf(pop_over_65_total / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(pop_over_65_total / area_km2, osm_users / area_km2, (area_km2 != 0 and area_km2 != 0)),
	   corrIf(pop_over_65_total / total_road_length, osm_users / area_km2, (total_road_length != 0 and area_km2 != 0)),
	   corrIf(pop_over_65_total / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(pop_over_65_total / populated_area_km2, osm_users / area_km2, (populated_area_km2 != 0 and area_km2 != 0)),
	   corrIf(poverty_families_total / total_road_length, osm_users / area_km2, (total_road_length != 0 and area_km2 != 0)),
	   corrIf(poverty_families_total / area_km2, osm_users / area_km2, (area_km2 != 0 and area_km2 != 0)),
	   corrIf(poverty_families_total / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(poverty_families_total / populated_area_km2, osm_users / area_km2, (populated_area_km2 != 0 and area_km2 != 0)),
	   corrIf(poverty_families_total / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(pop_disability_total / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(pop_disability_total / area_km2, osm_users / area_km2, (area_km2 != 0 and area_km2 != 0)),
	   corrIf(pop_disability_total / total_road_length, osm_users / area_km2, (total_road_length != 0 and area_km2 != 0)),
	   corrIf(pop_disability_total / populated_area_km2, osm_users / area_km2, (populated_area_km2 != 0 and area_km2 != 0)),
	   corrIf(pop_disability_total / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(pop_not_well_eng_speak / total_road_length, osm_users / area_km2, (total_road_length != 0 and area_km2 != 0)),
	   corrIf(pop_not_well_eng_speak / area_km2, osm_users / area_km2, (area_km2 != 0 and area_km2 != 0)),
	   corrIf(pop_not_well_eng_speak / populated_area_km2, osm_users / area_km2, (populated_area_km2 != 0 and area_km2 != 0)),
	   corrIf(pop_not_well_eng_speak / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(pop_not_well_eng_speak / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(pop_without_car / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(pop_without_car / total_road_length, osm_users / area_km2, (total_road_length != 0 and area_km2 != 0)),
	   corrIf(pop_without_car / populated_area_km2, osm_users / area_km2, (populated_area_km2 != 0 and area_km2 != 0)),
	   corrIf(pop_without_car / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(pop_without_car / area_km2, osm_users / area_km2, (area_km2 != 0 and area_km2 != 0)),
	   corrIf(days_maxtemp_over_32c_1c / one, osm_users / area_km2, (one != 0 and area_km2 != 0)),
	   corrIf(days_maxtemp_over_32c_2c / one, osm_users / area_km2, (one != 0 and area_km2 != 0)),
	   corrIf(days_mintemp_above_25c_1c / one, osm_users / area_km2, (one != 0 and area_km2 != 0)),
	   corrIf(days_mintemp_above_25c_2c / one, osm_users / area_km2, (one != 0 and area_km2 != 0)),
	   corrIf(mandays_maxtemp_over_32c_1c / populated_area_km2, osm_users / area_km2, (populated_area_km2 != 0 and area_km2 != 0)),
	   corrIf(mandays_maxtemp_over_32c_1c / area_km2, osm_users / area_km2, (area_km2 != 0 and area_km2 != 0)),
	   corrIf(mandays_maxtemp_over_32c_1c / population, osm_users / area_km2, (population != 0 and area_km2 != 0)),
	   corrIf(mandays_maxtemp_over_32c_1c / total_building_count, osm_users / area_km2, (total_building_count != 0 and area_km2 != 0)),
	   corrIf(mandays_maxtemp_over_32c_1c / total_road_length, osm_users / area_km2, (total_road_length != 0 and area_km2 != 0))
	   from stat_h3_indicators sth inner join h3_id{} h on (sth.h3=h.h3)""".format(indx))
                    print(cursor_click.fetchall())
                    print('Thread: {} Lead time: {}'.format(indx, datetime.now() - start))
