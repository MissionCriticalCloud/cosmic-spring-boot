package com.github.missioncriticalcloud.cosmic.api.usage.repositories.jdbc.mappers;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.github.missioncriticalcloud.cosmic.usage.core.model.Volume;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class VolumeMapper implements RowMapper<Volume> {

    @Override
    public Volume mapRow(final ResultSet resultSet, final int rowNum) throws SQLException {
        final Volume volume = new Volume();
        volume.setUuid(resultSet.getString("uuid"));
        volume.setName(resultSet.getString("name"));
        volume.setSize(BigDecimal.valueOf(resultSet.getLong("size")));

        return volume;
    }

}
