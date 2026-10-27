-- Chỉ dùng cho DB local/test. Chạy lại sẽ thêm bản ghi, không tự loại trùng.
INSERT INTO customer_trading_value_history (
    customer_id, branch_id, pcc_id, total_trading_value
)
VALUES
    (1001, '0101', '5603', 6700000.00),
    (1002, '0009', '0103', 26600820.00),
    (1001, '0101', '5603', 911450000.00),
    (1003, '0010', '5601', 1120000.00),
    (1004, '0003', '0501', 167175000.00),
    (1002, '0009', '0103', 0.00),
    (1005, '0002', '1301', 123456789012345678.90);
