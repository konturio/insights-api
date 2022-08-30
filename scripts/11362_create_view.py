from contextlib import closing

import psycopg2

with closing(psycopg2.connect(dbname='insights-api', user='',
                              password='', host='localhost', port=55432)) as conn_pg:
    with conn_pg.cursor() as cursor_pg:
        cursor_pg.execute('select param_id from bivariate_indicators')
        rows_pg = cursor_pg.fetchall()
        request = 'create view stat_h3_view as '
        with_select_list = []
        main_select_list = []
        join_list = []
        for ind_name in rows_pg:
            print(ind_name[0])
            with_select_list.append("""{}_tmp as (select x.h3 as h3, x.indicator_value as indicator_value from stat_h3_transposed x 
            join bivariate_indicators y on (x.indicator_uuid = y.param_uuid) where y.param_id = '{}')""".format(ind_name[0], ind_name[0]))
            main_select_list.append("""{}_tmp.indicator_value as {}""".format(ind_name[0], ind_name[0]))
            join_list.append("""left join {}_tmp on ({}_tmp.h3 = y.h3)""".format(ind_name[0], ind_name[0]))
        with_str = 'with '
        main_request = 'create materialized view stat_h3_view as ' + with_str + ', '.join(with_select_list) + ' select '\
                       + ', '.join(main_select_list) + ', y.h3, y.geom, y.zoom, y.resolution from stat_h3_geom y ' \
                       + ' '.join(join_list) + ' where y.h3 is not null'
        cursor_pg.execute(main_request)
        conn_pg.commit()
        # print(main_request)

