# File mẫu cho API sản phẩm

Gửi `POST /api/product-scans`, multipart field `file`.

| File | Kết quả kỳ vọng |
| --- | --- |
| `01-valid.xlsx` | 200, hai sản phẩm ID 1 và 2, giá 125000 và 89000 |
| `02-header-only.xlsx` | 200, `data = []` |
| `03-duplicate-header.xlsx` | 400, `INVALID_EXCEL` |
| `04-missing-header.xlsx` | 400, `INVALID_EXCEL` |
| `05-wrong-type.csv` | 400, `INVALID_FILE_TYPE` |
| `06-invalid-excel.xlsx` | 400, `INVALID_EXCEL` |
| `07-too-large.xlsx` | 413, `FILE_TOO_LARGE` |

File 07 là dữ liệu giả vượt 10 MB một byte để kiểm tra giới hạn upload.
Thiếu field `file` trả 400, `INVALID_EXCEL`.
Contract chi tiết: [API sản phẩm](../docs/api-contract.md).
