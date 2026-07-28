package org.example.server.repository;

import org.example.server.model.dto.Chat;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DeviceRepository {
    private final JdbcTemplate jdbcTemplate;

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Chat> getDeviceChats(String deviceId) {
        String sql = """
            SELECT p.chat_id, p.is_admin
            FROM person_device pd
            JOIN person p ON pd.person_name = p.name
            WHERE pd.device_id = ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, _) -> new Chat(
                        rs.getLong("chat_id"),
                        rs.getBoolean("is_admin")
                ),
                deviceId
        );
    }

    public Optional<Boolean> getHasClock(String deviceId) {
        String sql = """
            SELECT has_clock
            FROM device
            WHERE id = ?
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Boolean.class, deviceId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

}
