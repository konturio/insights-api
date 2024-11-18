INSERT INTO %s (param_id, param_label, copyrights, direction, is_base, internal_id,
                external_id, owner, state, is_public, allowed_users, date, description,
                coverage, update_frequency, application, unit_id, emoji, last_updated, upload_id)
VALUES (?, ?, ?::json, ?::json, ?, gen_random_uuid(), ?::uuid, ?, 'COPY IN PROGRESS', ?, ?::json, now(), ?, ?, ?, ?::json, ?, ?, ?::timestamptz, ?::uuid)
RETURNING internal_id::text;
