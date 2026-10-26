# Dữ liệu test Postman

Gửi `POST http://localhost:8080/api/excel-scans`, Body → form-data,
key `file`, kiểu File. Để Postman tự tạo Content-Type.

| File | Kết quả kỳ vọng |
| --- | --- |
| `01-valid.xlsx` | 200, `SCAN_SUCCESS`, `data` có 2 dòng; giữ mã `0001`, `0002`, note trống là `""` |
| `02-header-only.xlsx` | 200, `SCAN_SUCCESS`, `data = []` |
| `03-duplicate-header.xlsx` | 400, `INVALID_EXCEL` — tên header trùng |
| `04-missing-header.xlsx` | 400, `INVALID_EXCEL` — tên header cột 2 trống |
| `05-wrong-type.csv` | 400, `INVALID_FILE_TYPE` |
| `06-invalid-excel.xlsx` | 400, `INVALID_EXCEL` — file text đổi đuôi, không phải workbook |
| `07-too-large.xlsx` | 413, `FILE_TOO_LARGE` — 10.485.761 byte, vượt giới hạn 1 byte |

File 07 là nội dung giả để kiểm tra giới hạn upload, không phải workbook hợp lệ.
Kiểm tra thiếu file: bỏ key `file` khỏi request multipart; kỳ vọng 400, `INVALID_EXCEL`.

Nội dung `data` kỳ vọng của file 01:

```json
[
  {
    "productCode": "0001",
    "productName": "Sản phẩm A",
    "quantity": "10",
    "unitPrice": "125000",
    "note": "Dữ liệu thử"
  },
  {
    "productCode": "0002",
    "productName": "Sản phẩm B",
    "quantity": "5",
    "unitPrice": "89000",
    "note": ""
  }
]
```

Đã kiểm tra 7 file bằng cách gọi trực tiếp `ExcelScanService`.
Chưa gửi các file mẫu này qua Postman UI.
