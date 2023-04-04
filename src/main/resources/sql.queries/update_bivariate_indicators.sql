UPDATE %s
SET param_label = :label, copyrights = :copyrights::json,
    direction = :direction::json, is_base = :isBase, state = 'NEW', is_public = :isPublic,
    allowed_users = :allowedUsers::json, date = now(), description = :description,
    coverage = :coverage, update_frequency = :updateFrequency,
    application = :application, unit_id = :unitId, last_updated = :lastUpdated
WHERE param_id = :id AND owner = '%s' RETURNING param_uuid