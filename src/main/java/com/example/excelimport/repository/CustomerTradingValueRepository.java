package com.example.excelimport.repository;

import com.example.excelimport.dto.CustomerTradingValueData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerTradingValueRepository {

    private static final String SELECT_ALL = """
            SELECT id, customer_id, branch_id, pcc_id, total_trading_value
            FROM customer_trading_value_history
            ORDER BY id ASC
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Đọc toàn bộ bảng trong một snapshot, xử lý lần lượt từng dòng. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void readAllTradingValues(Consumer<CustomerTradingValueData> rowConsumer) {
        RowCallbackHandler rowCallback = resultSet -> {
            CustomerTradingValueData rowData = new CustomerTradingValueData(
                    resultSet.getLong("id"),
                    resultSet.getLong("customer_id"),
                    resultSet.getString("branch_id"),
                    resultSet.getString("pcc_id"),
                    resultSet.getBigDecimal("total_trading_value")
            );
            rowConsumer.accept(rowData);
        };

        jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    SELECT_ALL,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
            );
            // Connector/J dùng giá trị này để đọc từng dòng thay vì buffer cả bảng.
            statement.setFetchSize(Integer.MIN_VALUE);
            return statement;
        }, rowCallback);
    }
}
