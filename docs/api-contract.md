# Hợp đồng API scan Excel → JSON

API nhận file Excel khách hàng theo schema cố định và trả các dòng bên dưới thành DTO JSON.
Không kiểm tra nghiệp vụ khách hàng và không lưu dữ liệu vào database.

API đã triển khai luồng scan cơ bản. Giới hạn upload là 10 MB/file và 11 MB/request.

## Request

`POST /api/excel-scans-customer`

API xử lý đồng bộ và trả HTTP 200; không tạo bản ghi scan để lưu hoặc truy vấn lại.

- Content-Type: `multipart/form-data`, key `file`, kiểu File.
- Chỉ nhận một file `.xlsx` có nội dung workbook hợp lệ; không chấp nhận chỉ đổi đuôi file.
- Dung lượng file tối đa: 10 MB (10.485.760 byte), chấp nhận file đúng giới hạn.
- Giới hạn tổng multipart request cần lớn hơn giới hạn file để chứa phần metadata.

## Quy tắc đọc dữ liệu

1. Đọc sheet đầu tiên, không yêu cầu tên sheet cố định.
2. Dòng 1 bắt buộc gồm `name`, `email`, `phone`, `unitPrice`, theo đúng thứ tự, phân biệt hoa/thường.
   Bỏ khoảng trắng hai đầu tên cột; từ chối cột thiếu, thừa, trống, trùng hoặc sai thứ tự.
   Ô trống có định dạng phía sau header được bỏ qua.
3. Từ dòng 2, mỗi dòng map tường minh sang `CustomerData`, không dùng map/header động.
4. `name`, `email`, `phone` là chuỗi theo định dạng hiển thị Excel, ô trống trả `""`.
   `unitPrice` là JSON number, dùng `BigDecimal`; ô trống trả `null`, không fallback 0.
   Ô số lấy giá trị gốc, không phụ thuộc định dạng hiển thị. Giá dạng text dùng dấu chấm
   thập phân, không chứa dấu phân cách hàng nghìn hoặc ký hiệu tiền; sai kiểu trả `INVALID_EXCEL`.
   Không tự làm tròn hay quy định scale. Precision của ô số đã lưu trong Excel vẫn bị giới hạn bởi Excel.
5. Bỏ qua dòng hoàn toàn trống. File chỉ có header trả mảng rỗng `[]`.
   Giá dạng text chỉ chứa khoảng trắng được coi là `null`; dòng không còn dữ liệu sau map bị bỏ qua.
   Giá dạng số vẫn được đọc dù định dạng Excel ẩn hiển thị của ô, kể cả giá bằng 0.
6. Dữ liệu phải nằm trong các cột có header; nếu có giá trị ở cột thiếu header thì trả lỗi 400.
   Số/boolean/lỗi ở cột ngoài schema vẫn bị từ chối dù định dạng Excel ẩn hiển thị của ô.

Sheet được đọc có ô gộp vẫn bị từ chối với HTTP 400, errorCode `INVALID_EXCEL`.
Công thức trả kết quả đã lưu trong file, không tính lại và có thể cũ hoặc thiếu nếu file chưa lưu kết quả.
Ô lỗi ở trường chuỗi được giữ nguyên; ô lỗi hoặc boolean ở `unitPrice` trả `INVALID_EXCEL`.
Định dạng hiển thị dùng locale `en-US` để kết quả không phụ thuộc locale máy chủ.

## Response thành công

HTTP 200, Content-Type: `application/json`.
Body là mảng `CustomerData` trực tiếp theo thứ tự dòng Excel, không có lớp bọc response;
không gồm dòng header và dòng hoàn toàn trống.

Ví dụ file đầu vào:

| name | email | phone | unitPrice |
| --- | --- | --- | --- |
| Nguyễn Văn A | a@example.com | 0901234567 | 125000.125 |
| Trần Thị B | b@example.com | | |

```json
[
  {
    "name": "Nguyễn Văn A",
    "email": "a@example.com",
    "phone": "0901234567",
    "unitPrice": 125000.125
  },
  {
    "name": "Trần Thị B",
    "email": "b@example.com",
    "phone": "",
    "unitPrice": null
  }
]
```

Bốn trường trên là schema cố định. File có header tùy ý từ contract cũ không còn được chấp nhận.
Để giữ số 0 đầu, đặt dữ liệu như số điện thoại ở dạng Text trong Excel.

## Lỗi cơ bản

Còn 3 mã lỗi đầu vào: sai đuôi, file Excel không hợp lệ và vượt dung lượng.
Thiếu/rỗng file hoặc header không hợp lệ dùng chung mã `INVALID_EXCEL`.

| HTTP | errorCode | Trường hợp |
| --- | --- | --- |
| 400 | INVALID_FILE_TYPE | File không phải `.xlsx` |
| 400 | INVALID_EXCEL | Thiếu/rỗng file, multipart lỗi, workbook/header không hợp lệ, unitPrice sai kiểu hoặc sheet có ô gộp |
| 413 | FILE_TOO_LARGE | File vượt 10 MB |

Response lỗi dùng cấu trúc riêng theo mẫu exception, không có `data` và `errors`:

```json
{
  "code": -1,
  "errorCode": "INVALID_EXCEL",
  "message": "INVALID_EXCEL",
  "messageDetail": "File Excel không hợp lệ."
}
```

`code` là số `-1`; `errorCode` giữ ba mã định danh hiện có.
`message` là khóa thông báo, `messageDetail` là chi tiết tiếng Việt mặc định từ enum
hoặc chi tiết do code ứng dụng chủ động cung cấp. Chưa tích hợp nguồn tra đa ngôn ngữ.
Đây là thay đổi contract lỗi đã được chốt: client cần đọc `errorCode` thay vì `code` để phân loại lỗi.
Response thành công là mảng trực tiếp. Client cũ cần đọc body thay vì trường `data`. Không có bộ đếm import, kiểm tra email trùng hay lỗi ghi database.

Request JSON thay vì upload multipart trả HTTP 400, errorCode `INVALID_EXCEL`.

Các lỗi HTTP 404 (không có endpoint), 405 (sai method) và 500 (lỗi hệ thống)
do Spring xử lý mặc định, không dùng cấu trúc `APIErrorResponse` ở trên.
