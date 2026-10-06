# Tài Liệu Cơ Sở Dữ Liệu: Mô Hình Database Per Service

## 1. Tổng Quan Kiến Trúc

Hệ thống Ride-Hailing & Logistics áp dụng mẫu hình kiến trúc chuẩn công nghiệp **Database per Service Pattern**. Thay vì
sử dụng chung một cơ sở dữ liệu lớn, mỗi microservice sở hữu một cơ sở dữ liệu PostgreSQL độc lập:

| # | Microservice       | Port | Database Name | Chức năng dữ liệu chính                                                         |
|---|--------------------|------|---------------|---------------------------------------------------------------------------------|
| 1 | `iam-service`      | 8081 | `iam_db`      | Users, Roles, User_Roles, Refresh_Tokens, Audit_Logs                            |
| 2 | `driver-service`   | 8082 | `driver_db`   | Driver_Profiles, Vehicles, Driver_Documents, Driver_Audit_Logs                  |
| 3 | `location-service` | 8083 | `location_db` | Driver_Locations, Driver_Telemetry_History (PostGIS Geometry GiST Index)        |
| 4 | `pricing-service`  | 8084 | `pricing_db`  | Pricing_Rules, Fare_Quotes (Hệ thống tính giá động Surge Pricing)               |
| 5 | `trip-service`     | 8085 | `trip_db`     | Trips, Trip_Stops, Driver_Offers, Trip_Status_History, Outbox_Events            |
| 6 | `payment-service`  | 8086 | `payment_db`  | Wallets, Wallet_Entries (Immutable Ledger), Payments, Refunds, Idempotency_Keys |

---

## 2. Các Tệp DDL Khởi Tạo

- `00_init_multiple_databases.sh`: Script chạy tự động trong container Docker PostgreSQL khi khởi động lần đầu để tạo 6
  databases.
- `01_iam_db.sql`: Cấu trúc bảng và seed data quản trị cho `iam_db`.
- `02_driver_db.sql`: Cấu trúc hồ sơ tài xế và phương tiện cho `driver_db`.
- `03_location_db.sql`: Tích hợp extension PostGIS và chỉ mục GiST cho `location_db`.
- `04_pricing_db.sql`: Bảng giá cơ sở và báo giá cho `pricing_db`.
- `05_trip_db.sql`: Vòng đời chuyến xe, Partial Unique Index (chống race condition gán trùng tài xế) và Transactional
  Outbox cho `trip_db`.
- `06_payment_db.sql`: Sổ cái tài chính bất biến (Append-only Ledger) và Idempotency cho `payment_db`.

---

## 3. Khởi Tạo Thủ Công Trên PostgreSQL Cục Bộ

Chạy script PowerShell:

```powershell
.\database\setup_database.ps1
```

Script sẽ tự động kết nối tới `127.0.0.1:5432` (`postgres/admin`), tạo lần lượt 6 databases và nạp toàn bộ cấu trúc bảng
và seed data.
