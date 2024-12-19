select
    jsonb_build_object(
                       'meta', jsonb_build_object('max_zoom', 8,
                                                  'min_zoom', 0),
                       'indicators', (
                           select jsonb_agg(jsonb_build_object('name', bi.param_id,
                                                               'label', bi.param_label,
                                                               'emoji', bi.emoji,
                                                               'max_res', 8,
                                                               'direction', bi.direction,
                                                               'copyrights', bi.copyrights,
                                                               'description', bi.description,
                                                               'coverage', bi.coverage,
                                                               'update_frequency', bi.update_frequency,
                                                               'unit', jsonb_build_object('id', bul.unit_id,
                                                                                          'shortName', bul.short_name,
                                                                                          'longName', bul.long_name)))
                           from bivariate_indicators_metadata bi left join bivariate_unit_localization bul on bi.unit_id = bul.unit_id
                           where bi.state = 'READY'
                       ),
                       'colors', jsonb_build_object(
                           'fallback', '#ccc',
                           'combinations', (
                               select jsonb_agg(jsonb_build_object('color', color,
                                                                               'color_comment', color_comment,
                                                                               'corner', corner))
                               from bivariate_colors)
                        ),
                       'initAxis',
                       jsonb_build_object('x', jsonb_build_object('label', x.label, 'quotient',
                                                                  jsonb_build_array(x.numerator, x.denominator),
                                                                  'quotients',
                                                                  jsonb_build_array(
                                                                          jsonb_build_object('name', bix1.param_id,
                                                                                             'label', bix1.param_label,
                                                                                             'emoji', bix1.emoji,
                                                                                             'max_res', 8,
                                                                                             'direction', bix1.direction,
                                                                                             'description', bix1.description,
                                                                                             'copyrights', bix1.copyrights,
                                                                                             'coverage', bix1.coverage,
                                                                                             'update_frequency', bix1.update_frequency,
                                                                                             'unit', jsonb_build_object('id', bulx1.unit_id,
                                                                                                                        'shortName', bulx1.short_name,
                                                                                                                        'longName', bulx1.long_name)),
                                                                          jsonb_build_object('name', bix2.param_id,
                                                                                             'label', bix2.param_label,
                                                                                             'emoji', bix2.emoji,
                                                                                             'max_res', 8,
                                                                                             'direction', bix2.direction,
                                                                                             'description', bix2.description,
                                                                                             'copyrights', bix2.copyrights,
                                                                                             'coverage', bix2.coverage,
                                                                                             'update_frequency', bix2.update_frequency,
                                                                                             'unit', jsonb_build_object('id', bulx2.unit_id,
                                                                                                                        'shortName', bulx2.short_name,
                                                                                                                        'longName', bulx2.long_name))
                                                                                   ),
                                                                  'transformation', x.default_transform,
                                                                  'steps',
                                                                  jsonb_build_array(
                                                                      jsonb_build_object('value', floor(x.min), 'label', x.min_label),
                                                                      jsonb_build_object('value', x.p25, 'label', x.p25_label),
                                                                      jsonb_build_object('value', x.p75, 'label', x.p75_label),
                                                                      jsonb_build_object('value', ceil(x.max), 'label', x.max_label))),
                                          'y', jsonb_build_object('label', y.label, 'quotient',
                                                                  jsonb_build_array(y.numerator, y.denominator),
                                                                  'quotients',
                                                                  jsonb_build_array(
                                                                          jsonb_build_object('name', biy1.param_id,
                                                                                             'label', biy1.param_label,
                                                                                             'emoji', biy1.emoji,
                                                                                             'max_res', 8,
                                                                                             'direction', biy1.direction,
                                                                                             'description', biy1.description,
                                                                                             'copyrights', biy1.copyrights,
                                                                                             'coverage', biy1.coverage,
                                                                                             'update_frequency', biy1.update_frequency,
                                                                                             'unit', jsonb_build_object('id', buly1.unit_id,
                                                                                                                        'shortName', buly1.short_name,
                                                                                                                        'longName', buly1.long_name)),
                                                                          jsonb_build_object('name', biy2.param_id,
                                                                                             'label', biy2.param_label,
                                                                                             'emoji', biy2.emoji,
                                                                                             'max_res', 8,
                                                                                             'direction', biy2.direction,
                                                                                             'description', biy2.description,
                                                                                             'copyrights', biy2.copyrights,
                                                                                             'coverage', biy2.coverage,
                                                                                             'update_frequency', biy2.update_frequency,
                                                                                             'unit', jsonb_build_object('id', buly2.unit_id,
                                                                                                                        'shortName', buly2.short_name,
                                                                                                                        'longName', buly2.long_name))
                                                                                   ),
                                                                  'transformation', y.default_transform,
                                                                  'steps',
                                                                  jsonb_build_array(
                                                                      jsonb_build_object('value', floor(y.min), 'label', y.min_label),
                                                                      jsonb_build_object('value', y.p25, 'label', y.p25_label),
                                                                      jsonb_build_object('value', y.p75, 'label', y.p75_label),
                                                                      jsonb_build_object('value', ceil(y.max), 'label', y.max_label)))
                           ),
                       'overlays', ov.overlay
        )::text
from
    ( select
          json_agg(
              jsonb_build_object('label', label, 'quotient', jsonb_build_array(numerator, denominator), 'quality',
                                 quality,
                                 'transformation', default_transform,
                                 'steps', jsonb_build_array(
                                     jsonb_build_object('value', floor(min), 'label', min_label),
                                     jsonb_build_object('value', p25, 'label', p25_label),
                                     jsonb_build_object('value', p75, 'label', p75_label),
                                     jsonb_build_object('value', ceil(max), 'label', max_label)))) as axis
      from
          bivariate_axis_v2 b,
          bivariate_indicators_metadata m1,
          bivariate_indicators_metadata m2
      where
            m1.state = 'READY'
        and m2.state = 'READY'
        and b.numerator_uuid = m1.internal_id
        and b.denominator_uuid = m2.internal_id
    ) ba,
    ( select
          json_agg(jsonb_build_object('name', o.name, 'active', o.active, 'description', o.description,
                                      'colors', o.colors, 'order', o.ord,
                                      'x', jsonb_build_object('label', ax.label,
                                                              'quotient', jsonb_build_array(ax.numerator, ax.denominator),
                                                              'quotients',
                                                              jsonb_build_array(
                                                                      jsonb_build_object('name', bix1.param_id,
                                                                                         'label', bix1.param_label,
                                                                                         'emoji', bix1.emoji,
                                                                                         'max_res', 8,
                                                                                         'direction', bix1.direction,
                                                                                         'description', bix1.description,
                                                                                         'copyrights', bix1.copyrights,
                                                                                         'coverage', bix1.coverage,
                                                                                         'update_frequency', bix1.update_frequency,
                                                                                         'unit', jsonb_build_object('id', bulx1.unit_id,
                                                                                                                    'shortName', bulx1.short_name,
                                                                                                                    'longName', bulx1.long_name)),
                                                                      jsonb_build_object('name', bix2.param_id,
                                                                                         'label', bix2.param_label,
                                                                                         'emoji', bix2.emoji,
                                                                                         'max_res', 8,
                                                                                         'direction', bix2.direction,
                                                                                         'description', bix2.description,
                                                                                         'copyrights', bix2.copyrights,
                                                                                         'coverage', bix2.coverage,
                                                                                         'update_frequency', bix2.update_frequency,
                                                                                         'unit', jsonb_build_object('id', bulx2.unit_id,
                                                                                                                    'shortName', bulx2.short_name,
                                                                                                                    'longName', bulx2.long_name))
                                                                               ),
                                                              'transformation', ax.default_transform,
                                                              'steps',
                                                              jsonb_build_array(
                                                                      jsonb_build_object('value', floor(ax.min), 'label', ax.min_label),
                                                                      jsonb_build_object('value', ax.p25, 'label', ax.p25_label),
                                                                      jsonb_build_object('value', ax.p75, 'label', ax.p75_label),
                                                                      jsonb_build_object('value', ceil(ax.max), 'label', ax.max_label))),
                                      'y', jsonb_build_object('label', ay.label,
                                                              'quotient', jsonb_build_array(ay.numerator, ay.denominator),
                                                              'quotients',
                                                              jsonb_build_array(
                                                                      jsonb_build_object('name', biy1.param_id,
                                                                                         'label', biy1.param_label,
                                                                                         'emoji', biy1.emoji,
                                                                                         'max_res', 8,
                                                                                         'direction', biy1.direction,
                                                                                         'description', biy1.description,
                                                                                         'copyrights', biy1.copyrights,
                                                                                         'coverage', biy1.coverage,
                                                                                         'update_frequency', biy1.update_frequency,
                                                                                         'unit', jsonb_build_object('id', buly1.unit_id,
                                                                                                                    'shortName', buly1.short_name,
                                                                                                                    'longName', buly1.long_name)),
                                                                      jsonb_build_object('name', biy2.param_id,
                                                                                         'label', biy2.param_label,
                                                                                         'emoji', biy2.emoji,
                                                                                         'max_res', 8,
                                                                                         'direction', biy2.direction,
                                                                                         'description', biy2.description,
                                                                                         'copyrights', biy2.copyrights,
                                                                                         'coverage', biy2.coverage,
                                                                                         'update_frequency', biy2.update_frequency,
                                                                                         'unit', jsonb_build_object('id', buly2.unit_id,
                                                                                                                    'shortName', buly2.short_name,
                                                                                                                    'longName', buly2.long_name))
                                                                               ),
                                                              'transformation', ay.default_transform,
                                                              'steps',
                                                              jsonb_build_array(
                                                                      jsonb_build_object('value', floor(ay.min), 'label', ay.min_label),
                                                                      jsonb_build_object('value', ay.p25, 'label', ay.p25_label),
                                                                      jsonb_build_object('value', ay.p75, 'label', ay.p75_label),
                                                                      jsonb_build_object('value', ceil(ay.max), 'label', ay.max_label))))
                   order by ord) as overlay
      from
          bivariate_axis_v2     ax,
          bivariate_axis_v2     ay,
          bivariate_overlays_v2 o,
          bivariate_indicators_metadata bix1 left join bivariate_unit_localization bulx1 on bix1.unit_id = bulx1.unit_id,
          bivariate_indicators_metadata bix2 left join bivariate_unit_localization bulx2 on bix2.unit_id = bulx2.unit_id,
          bivariate_indicators_metadata biy1 left join bivariate_unit_localization buly1 on biy1.unit_id = buly1.unit_id,
          bivariate_indicators_metadata biy2 left join bivariate_unit_localization buly2 on biy2.unit_id = buly2.unit_id
      where
            bix1.external_id = o.x_numerator_id
        and bix2.external_id = o.x_denominator_id
        and biy1.external_id = o.y_numerator_id
        and biy2.external_id = o.y_denominator_id
        and bix1.state = 'READY'
        and bix2.state = 'READY'
        and biy1.state = 'READY'
        and biy2.state = 'READY'
        and ax.numerator_uuid = bix1.internal_id
        and ax.denominator_uuid = bix2.internal_id
        and ay.numerator_uuid = biy1.internal_id
        and ay.denominator_uuid = biy2.internal_id )                                                      ov,
    bivariate_axis_v2                                                                              x,
    bivariate_axis_v2                                                                              y,
    bivariate_indicators_metadata bix1 left join bivariate_unit_localization bulx1 on bix1.unit_id = bulx1.unit_id,
    bivariate_indicators_metadata bix2 left join bivariate_unit_localization bulx2 on bix2.unit_id = bulx2.unit_id,
    bivariate_indicators_metadata biy1 left join bivariate_unit_localization buly1 on biy1.unit_id = buly1.unit_id,
    bivariate_indicators_metadata biy2 left join bivariate_unit_localization buly2 on biy2.unit_id = buly2.unit_id
where
      x.numerator_uuid = (select internal_id from bivariate_indicators_metadata where param_id = 'count' and state = 'READY' limit 1)
  and x.denominator_uuid = (select internal_id from bivariate_indicators_metadata where param_id = 'area_km2' and owner='disaster.ninja' limit 1)
  and y.numerator_uuid = (select internal_id from bivariate_indicators_metadata where param_id = 'view_count' and state = 'READY' limit 1)
  and y.denominator_uuid = (select internal_id from bivariate_indicators_metadata where param_id = 'area_km2' and owner='disaster.ninja' limit 1)
  and x.numerator_uuid = bix1.internal_id
  and x.denominator_uuid = bix2.internal_id
  and y.numerator_uuid = biy1.internal_id
  and y.denominator_uuid = biy2.internal_id
