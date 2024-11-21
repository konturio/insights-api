package io.kontur.insightsapi.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.IndicatorState;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class BivariateIndicatorRowMapper implements RowMapper<BivariateIndicatorDto> {

    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Override
    //TODO: update after merging wrk and test tables of bivariate_indicators
    public BivariateIndicatorDto mapRow(ResultSet resultSet, int rowNum) {
        BivariateIndicatorDto bivariateIndicatorDto = new BivariateIndicatorDto();
        bivariateIndicatorDto.setId(resultSet.getString(BivariateIndicatorsColumns.param_id.name()));
        bivariateIndicatorDto.setLabel(resultSet.getString(BivariateIndicatorsColumns.param_label.name()));
        bivariateIndicatorDto.setCopyrights(resultSet.getString(BivariateIndicatorsColumns.copyrights.name()) == null
                ? null : objectMapper.readValue(resultSet.getString(BivariateIndicatorsColumns.copyrights.name()),
                new TypeReference<>() {
                }));
        bivariateIndicatorDto.setDirection(resultSet.getString(BivariateIndicatorsColumns.direction.name()) == null
                ? null : objectMapper.readValue(resultSet.getString(BivariateIndicatorsColumns.direction.name()),
                new TypeReference<>() {
                }));
        bivariateIndicatorDto.setIsBase(resultSet.getBoolean(BivariateIndicatorsColumns.is_base.name()));
        bivariateIndicatorDto.setExternalId(resultSet.getString(BivariateIndicatorsColumns.external_id.name()));
        bivariateIndicatorDto.setInternalId(resultSet.getString(BivariateIndicatorsColumns.internal_id.name()));
        bivariateIndicatorDto.setOwner(resultSet.getString(BivariateIndicatorsColumns.owner.name()));

        String state = resultSet.getString(BivariateIndicatorsColumns.state.name()).replace(" ", "_");
        bivariateIndicatorDto.setState(state != null ? IndicatorState.valueOf(state) : null);

        bivariateIndicatorDto.setIsPublic(resultSet.getBoolean(BivariateIndicatorsColumns.is_public.name()));
        bivariateIndicatorDto.setAllowedUsers(resultSet.getString(BivariateIndicatorsColumns.allowed_users.name())
                == null ? null : objectMapper.readValue(resultSet.getString(BivariateIndicatorsColumns
                .allowed_users.name()), new TypeReference<>() {
        }));
        bivariateIndicatorDto.setDate(resultSet.getObject(BivariateIndicatorsColumns.date.name(),
                OffsetDateTime.class));
        bivariateIndicatorDto.setDescription(resultSet.getString(BivariateIndicatorsColumns.description.name()));
        bivariateIndicatorDto.setCoverage(resultSet.getString(BivariateIndicatorsColumns.coverage.name()));
        bivariateIndicatorDto.setUpdateFrequency(resultSet.getString(BivariateIndicatorsColumns
                .update_frequency.name()));
        bivariateIndicatorDto.setApplication(resultSet.getString(BivariateIndicatorsColumns.application.name()) == null
                ? null : objectMapper.readValue(resultSet.getString(BivariateIndicatorsColumns.application.name()),
                new TypeReference<>() {
                }));
        bivariateIndicatorDto.setUnitId(resultSet.getString(BivariateIndicatorsColumns.unit_id.name()));
        bivariateIndicatorDto.setEmoji(resultSet.getString(BivariateIndicatorsColumns.emoji.name()));
        bivariateIndicatorDto.setLastUpdated(resultSet.getObject(BivariateIndicatorsColumns.last_updated.name(),
                OffsetDateTime.class));
        return bivariateIndicatorDto;
    }

    private enum BivariateIndicatorsColumns {
        param_id, param_label, copyrights, direction, is_base, external_id, internal_id, owner, state, is_public,
        allowed_users, date, description, coverage, update_frequency, application, unit_id, emoji, last_updated
    }
}
