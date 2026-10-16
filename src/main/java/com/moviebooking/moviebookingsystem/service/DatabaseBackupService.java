package com.moviebooking.moviebookingsystem.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DatabaseBackupService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseBackupService(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public String exportDatabase() throws SQLException {

        StringBuilder sql = new StringBuilder();

        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {

            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"});

            List<String> tableNames = new ArrayList<>();

            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }

            tables.close();

            sql.append("-- CineBook Database Backup\n");
            sql.append("-- Generated: ");
            sql.append(java.time.LocalDateTime.now());
            sql.append("\n\n");

            for (String tableName : tableNames) {

                if (isSystemTable(tableName)) {
                    continue;
                }

                sql.append("-- Table: ");
                sql.append(tableName);
                sql.append("\n");

                List<String> columns = jdbcTemplate.queryForList(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                        String.class,
                        tableName
                );

                if (columns.isEmpty()) {
                    continue;
                }

                String columnList = String.join(", ", columns);

                List<java.util.Map<String, Object>> rows =
                        jdbcTemplate.queryForList("SELECT * FROM " + tableName);

                for (java.util.Map<String, Object> row : rows) {

                    sql.append("INSERT INTO ");
                    sql.append(tableName);
                    sql.append(" (");
                    sql.append(columnList);
                    sql.append(") VALUES (");

                    boolean first = true;

                    for (String column : columns) {

                        Object value = row.get(column);

                        if (!first) {
                            sql.append(", ");
                        }

                        first = false;

                        if (value == null) {
                            sql.append("NULL");
                        } else if (value instanceof Number) {
                            sql.append(value.toString());
                        } else {
                            String escaped = value.toString()
                                    .replace("'", "''");
                            sql.append("'");
                            sql.append(escaped);
                            sql.append("'");
                        }
                    }

                    sql.append(");\n");
                }

                sql.append("\n");
            }
        }

        return sql.toString();
    }

    private boolean isSystemTable(String tableName) {
        String lower = tableName.toLowerCase();
        return lower.startsWith("sys") || lower.startsWith("dt");
    }
}
