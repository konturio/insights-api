from contextlib import closing

import psycopg2

with closing(psycopg2.connect(dbname='insights-api', user='',
                              password='', host='localhost', port=55432)) as conn_pg:
    with conn_pg.cursor() as cursor_pg:
        cursor_pg.execute('select param_id, param_uuid from bivariate_indicators')
        rows_pg = cursor_pg.fetchall()
        for ind_name in rows_pg:
            print(ind_name[0])
            cursor_pg.execute("""insert into stat_h3_transposed (
            h3, indicator_uuid, indicator_value)
            select h3, '{}', {} from stat_h3""".format(ind_name[1], ind_name[0]))
        conn_pg.commit()