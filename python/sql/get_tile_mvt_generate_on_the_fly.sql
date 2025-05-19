with %s
select ST_AsMVT(q, 'stats', 8192, 'geom', 'h3ind') as tile
from indicators_as_columns q;
