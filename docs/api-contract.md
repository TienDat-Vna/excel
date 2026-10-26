# Hợp đồng API scan Excel → JSON

API nhận file Excel, đọc tên trường ở dòng đầu tiên và trả các dòng bên dưới thành JSON.
Không kiểm tra nghiệp vụ khách hàng và không lưu dữ liệu vào database.

API đã triển khai luồng scan cơ bản. Giới hạn upload là 10 MB/file và 11 MB/request.

## Request

`POST /api/excel-scans`

API xử lý đồng bộ và trả HTTP 200; không tạo bản ghi scan để lưu hoặc truy vấn lại.

- Content-Type: `multipart/form-data`, key `file`, kiểu File.
- Chỉ nhận một file `.xlsx` có nội dung workbook hợp lệ; không chấp nhận chỉ đổi đuôi file.
- Dung lượng file tối đa: 10 MB (10.485.760 byte), chấp nhận file đúng giới hạn.
- Giới hạn tổng multipart request cần lớn hơn giới hạn file để chứa phần metadata.

## Quy tắc đọc dữ liệu

1. Đọc sheet đầu tiên, không yêu cầu tên sheet cố định.
2. Dòng 1 là header: mỗi ô là tên một trường JSON, không cố định `name`, `email`, `phone`.
   Bỏ khoảng trắng ở hai đầu tên trường; tên trường phải có giá trị và không trùng nhau.
3. Từ dòng 2, mỗi dòng dữ liệu trở thành một object; ánh xạ giá trị theo vị trí cột của header.
4. Trả giá trị ô dưới dạng chuỗi theo định dạng hiển thị trong Excel; ô trống trả `""`.
   Không tự chuyển kiểu, chuẩn hóa hay validate nghiệp vụ của giá trị.
5. Bỏ qua dòng hoàn toàn trống. File chỉ có header trả mảng `data` rỗng.
6. Dữ liệu phải nằm trong các cột có header; nếu có giá trị ở cột thiếu header thì trả lỗi 400.

Sheet được đọc có ô gộp vẫn bị từ chối với HTTP 400, code `INVALID_EXCEL`.
Công thức trả kết quả đã lưu trong file, không tính lại và có thể cũ hoặc thiếu nếu file chưa lưu kết quả.
Ô lỗi Excel được trả thành chuỗi, ví dụ `#DIV/0!` hoặc `#N/A`, không làm request thất bại.
Định dạng hiển thị dùng locale `en-US` để kết quả không phụ thuộc locale máy chủ.

## Response thành công

HTTP 200, Content-Type: `application/json`.
Giữ cấu trúc response hiện có: `code`, `message`, `data`, `errors`.
`data` là mảng object theo thứ tự dòng Excel; không gồm dòng header và dòng hoàn toàn trống.

Ví dụ file đầu vào:

| name | email | phone |
| --- | --- | --- |
| Nguyễn Văn A | a@example.com | 0901234567 |
| Trần Thị B | b@example.com | |

```json
{
  "code": "SCAN_SUCCESS",
  "message": "Đọc file Excel thành công",
  "data": [
    {
      "name": "Nguyễn Văn A",
      "email": "a@example.com",
      "phone": "0901234567"
    },
    {
      "name": "Trần Thị B",
      "email": "b@example.com",
      "phone": ""
    }
  ],
  "errors": []
}
```

Các tên trường trên chỉ là ví dụ; JSON sử dụng header thực tế của file.
Để giữ số 0 đầu, đặt dữ liệu như số điện thoại ở dạng Text trong Excel.

## Lỗi cơ bản

Còn 3 mã lỗi đầu vào: sai đuôi, file Excel không hợp lệ và vượt dung lượng.
Thiếu/rỗng file hoặc header không hợp lệ dùng chung mã `INVALID_EXCEL`.

| HTTP | Code | Trường hợp |
| --- | --- | --- |
| 400 | INVALID_FILE_TYPE | File không phải `.xlsx` |
| 400 | INVALID_EXCEL | Thiếu/rỗng file, multipart lỗi, workbook/header không hợp lệ hoặc sheet có ô gộp |
| 413 | FILE_TOO_LARGE | File vượt 10 MB |

Response lỗi dùng cùng cấu trúc, `data = null`, `errors = []`; `code` và `message`
mô tả lỗi tương ứng. Không có bộ đếm import, kiểm tra email trùng hay lỗi ghi database.

Request JSON thay vì upload multipart trả HTTP 400, code `INVALID_EXCEL`.

Các lỗi HTTP 404 (không có endpoint), 405 (sai method) và 500 (lỗi hệ thống)
do Spring xử lý mặc định, không dùng cấu trúc `ExcelDataResponse` ở trên.
