-- ============================================================================
-- SEED DATA FOR RIDE-HAILING & LOGISTICS PLATFORM
-- Multi-Database Seed for Demo, Testing & Thesis Defense
-- Password for all accounts: password123 (BCrypt hash $2a$10$H8MMYjWdHb7yCj0xNXmMe.04nl4DQn0E9F2Cd8LNtgbw2gofxRc.K)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. IAM SERVICE (Database: iam_db)
-- ----------------------------------------------------------------------------
\connect iam_db;

-- Update or insert 4 Roles: ADMIN, SUPPORT_STAFF, CUSTOMER, DRIVER
INSERT INTO iam.users (id, phone, email, password_hash, full_name, role, status) VALUES
('00000000-0000-0000-0000-000000000001', '0909999999', 'admin@ridehailing.local',
 '$2a$10$H8MMYjWdHb7yCj0xNXmMe.04nl4DQn0E9F2Cd8LNtgbw2gofxRc.K', 'Trần Quản Trị', 'ADMIN', 'ACTIVE'),
('00000000-0000-0000-0000-000000000002', '0903333333', 'support@ridehailing.local',
 '$2a$10$H8MMYjWdHb7yCj0xNXmMe.04nl4DQn0E9F2Cd8LNtgbw2gofxRc.K', 'Lê Chăm Sóc Khách Hàng', 'SUPPORT_STAFF', 'ACTIVE'),
('00000000-0000-0000-0000-000000000003', '0901111111', 'customer@ridehailing.local',
 '$2a$10$H8MMYjWdHb7yCj0xNXmMe.04nl4DQn0E9F2Cd8LNtgbw2gofxRc.K', 'Nguyễn Văn Khách', 'CUSTOMER', 'ACTIVE'),
('00000000-0000-0000-0000-000000000004', '0902222222', 'driver1@ridehailing.local',
 '$2a$10$H8MMYjWdHb7yCj0xNXmMe.04nl4DQn0E9F2Cd8LNtgbw2gofxRc.K', 'Phạm Văn Tài (Bike)', 'DRIVER', 'ACTIVE'),
('00000000-0000-0000-0000-000000000005', '0905555555', 'driver2@ridehailing.local',
 '$2a$10$H8MMYjWdHb7yCj0xNXmMe.04nl4DQn0E9F2Cd8LNtgbw2gofxRc.K', 'Hoàng Văn Lái (Car 4)', 'DRIVER', 'ACTIVE'),
('00000000-0000-0000-0000-000000000006', '0906666666', 'driver.newbie@ridehailing.local',
 '$2a$10$H8MMYjWdHb7yCj0xNXmMe.04nl4DQn0E9F2Cd8LNtgbw2gofxRc.K', 'Vũ Văn Mới (Chờ duyệt KYC)', 'DRIVER', 'ACTIVE')
ON CONFLICT (id) DO UPDATE 
SET phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    status = EXCLUDED.status;

-- ----------------------------------------------------------------------------
-- 2. DRIVER SERVICE (Database: driver_db)
-- ----------------------------------------------------------------------------
\connect driver_db;

-- Hồ sơ Tài xế
INSERT INTO driver.driver_profiles 
(driver_id, user_id, id_card_number, date_of_birth, address, review_status, status_reason, availability_status, rating, total_trips) VALUES
('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000004', '001090000001', '1992-05-15', 'Số 12 Tràng Tiền, Hoàn Kiếm, Hà Nội', 'APPROVED', 'Hồ sơ đầy đủ, đạt tiêu chuẩn', 'AVAILABLE', 4.95, 150),
('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000005', '001090000002', '1988-10-20', 'Số 88 Cầu Giấy, Cầu Giấy, Hà Nội', 'APPROVED', 'Hồ sơ đầy đủ, đạt tiêu chuẩn', 'AVAILABLE', 4.88, 85),
('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000006', '001090000003', '1998-03-08', 'Số 45 Giải Phóng, Hai Bà Trưng, Hà Nội', 'PENDING_REVIEW', 'Mới nộp hồ sơ, chờ Admin thẩm định', 'OFFLINE', 5.00, 0)
ON CONFLICT (driver_id) DO UPDATE
SET review_status = EXCLUDED.review_status,
    availability_status = EXCLUDED.availability_status;

-- Phương tiện của Tài xế
INSERT INTO driver.vehicles 
(id, driver_id, type, license_plate, make, brand, model, year, color, status) VALUES
('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000004', 'BIKE', '29A1-99999', 'Honda', 'Honda', 'Air Blade 160', 2023, 'Đen nhám', 'ACTIVE'),
('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000005', 'CAR_4_SEAT', '30H-88888', 'Toyota', 'Toyota', 'Vios G', 2022, 'Trắng', 'ACTIVE'),
('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000006', 'BIKE', '29B2-77777', 'Yamaha', 'Yamaha', 'Exciter 155', 2024, 'Xanh GP', 'PENDING_APPROVAL')
ON CONFLICT (license_plate) DO UPDATE
SET status = EXCLUDED.status;

-- Giấy tờ đính kèm
INSERT INTO driver.driver_documents 
(id, driver_id, document_type, file_path, mime_type, file_size_bytes, status) VALUES
('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000004', 'DRIVER_LICENSE', 'https://storage.ridehailing.local/docs/gplx_04.jpg', 'image/jpeg', 204800, 'VERIFIED'),
('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000005', 'DRIVER_LICENSE', 'https://storage.ridehailing.local/docs/gplx_05.jpg', 'image/jpeg', 307200, 'VERIFIED'),
('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000006', 'DRIVER_LICENSE', 'https://storage.ridehailing.local/docs/gplx_06.jpg', 'image/jpeg', 256000, 'PENDING')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. LOCATION SERVICE (Database: location_db)
-- ----------------------------------------------------------------------------
\connect location_db;

-- Vị trí GPS hiện tại của 2 tài xế tại Hà Nội
INSERT INTO location.driver_latest_locations 
(driver_id, is_available, sequence_num, device_timestamp, server_timestamp, location, latitude, longitude, heading_degrees, speed_mps, accuracy_meters) VALUES
('00000000-0000-0000-0000-000000000004', TRUE, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 ST_SetSRID(ST_MakePoint(105.854444, 21.028511), 4326), 21.028511, 105.854444, 45.0, 5.5, 3.0),
('00000000-0000-0000-0000-000000000005', TRUE, 85, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
 ST_SetSRID(ST_MakePoint(105.852000, 21.030500), 4326), 21.030500, 105.852000, 90.0, 8.2, 4.5)
ON CONFLICT (driver_id) DO UPDATE
SET is_available = EXCLUDED.is_available,
    location = EXCLUDED.location,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude;

-- ----------------------------------------------------------------------------
-- 4. PRICING SERVICE (Database: pricing_db)
-- ----------------------------------------------------------------------------
\connect pricing_db;

-- Bảng Quy tắc Giá Cước (Pricing Rules) cho Hà Nội & Toàn quốc
INSERT INTO pricing.pricing_rules 
(id, service_type, region_code, base_fare, per_km_rate, per_minute_rate, minimum_fare, version, effective_from, is_active) VALUES
('00000000-0000-0000-0000-000000000401', 'RIDE_BIKE', 'VN-HN', 15000.00, 8000.00, 800.00, 15000.00, 1, CURRENT_TIMESTAMP, TRUE),
('00000000-0000-0000-0000-000000000402', 'RIDE_CAR_4', 'VN-HN', 25000.00, 12000.00, 1200.00, 25000.00, 1, CURRENT_TIMESTAMP, TRUE),
('00000000-0000-0000-0000-000000000403', 'RIDE_CAR_7', 'VN-HN', 35000.00, 15000.00, 1500.00, 35000.00, 1, CURRENT_TIMESTAMP, TRUE),
('00000000-0000-0000-0000-000000000404', 'DELIVERY_EXPRESS', 'VN-HN', 18000.00, 9000.00, 900.00, 18000.00, 1, CURRENT_TIMESTAMP, TRUE),
('00000000-0000-0000-0000-000000000405', 'RIDE_BIKE', 'VN-HCM', 15000.00, 8500.00, 850.00, 15000.00, 1, CURRENT_TIMESTAMP, TRUE),
('00000000-0000-0000-0000-000000000406', 'RIDE_CAR_4', 'VN-HCM', 25000.00, 12500.00, 1250.00, 25000.00, 1, CURRENT_TIMESTAMP, TRUE)
ON CONFLICT (service_type, region_code, version) DO UPDATE
SET base_fare = EXCLUDED.base_fare,
    per_km_rate = EXCLUDED.per_km_rate,
    is_active = EXCLUDED.is_active;

-- Báo giá mẫu (Fare Quote) còn hạn phục vụ test
INSERT INTO pricing.fare_quotes
(id, customer_id, service_type, pickup_point, pickup_address, dropoff_point, dropoff_address,
 estimated_distance_meters, estimated_duration_seconds, base_price, distance_price, duration_price,
 surge_multiplier, total_fare, currency, rule_version, expires_at, is_used) VALUES
('00000000-0000-0000-0000-000000000499', '00000000-0000-0000-0000-000000000003', 'RIDE_BIKE',
 ST_SetSRID(ST_MakePoint(105.854444, 21.028511), 4326), 'Nhà hát Lớn Hà Nội, Hoàn Kiếm',
 ST_SetSRID(ST_MakePoint(105.801889, 21.036667), 4326), 'Đại học Giao thông Vận tải, Đống Đa',
 6500, 975, 15000.00, 52000.00, 13000.00, 1.00, 80000.00, 'VND', 1,
 CURRENT_TIMESTAMP + INTERVAL '7 days', FALSE)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5. TRIP SERVICE (Database: trip_db)
-- ----------------------------------------------------------------------------
\connect trip_db;

-- Chuyến đi mẫu đã hoàn thành (COMPLETED) trong lịch sử
INSERT INTO trip.trips
(id, trip_code, customer_id, driver_id, quote_id, service_type, status, idempotency_key, quote_snapshot,
 pickup_point, final_fare, currency, payment_method, requested_at, accepted_at, completed_at) VALUES
('00000000-0000-0000-0000-000000000501', 'TRIP-20261027-001',
 '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000004',
 '00000000-0000-0000-0000-000000000499', 'RIDE_BIKE', 'COMPLETED', 'IDEMP-TRIP-HIST-001',
 '{"totalFare": 80000.00, "serviceType": "RIDE_BIKE", "baseFare": 15000.00}',
 ST_SetSRID(ST_MakePoint(105.854444, 21.028511), 4326), 80000.00, 'VND', 'CASH',
 CURRENT_TIMESTAMP - INTERVAL '2 hours',
 CURRENT_TIMESTAMP - INTERVAL '115 minutes',
 CURRENT_TIMESTAMP - INTERVAL '90 minutes')
ON CONFLICT (trip_code) DO NOTHING;

-- Lịch sử trạng thái của chuyến đi mẫu
INSERT INTO trip.trip_status_history
(trip_id, from_status, to_status, actor_id, actor_role, reason, created_at) VALUES
('00000000-0000-0000-0000-000000000501', 'CREATED', 'MATCHING', '00000000-0000-0000-0000-000000000003', 'CUSTOMER', 'Customer requested ride', CURRENT_TIMESTAMP - INTERVAL '120 minutes'),
('00000000-0000-0000-0000-000000000501', 'MATCHING', 'ACCEPTED', '00000000-0000-0000-0000-000000000004', 'DRIVER', 'Driver accepted offer', CURRENT_TIMESTAMP - INTERVAL '115 minutes'),
('00000000-0000-0000-0000-000000000501', 'ACCEPTED', 'PICKING_UP', '00000000-0000-0000-0000-000000000004', 'DRIVER', 'Driver moving to pickup', CURRENT_TIMESTAMP - INTERVAL '110 minutes'),
('00000000-0000-0000-0000-000000000501', 'PICKING_UP', 'ARRIVED', '00000000-0000-0000-0000-000000000004', 'DRIVER', 'Driver arrived at pickup location', CURRENT_TIMESTAMP - INTERVAL '105 minutes'),
('00000000-0000-0000-0000-000000000501', 'ARRIVED', 'IN_TRIP', '00000000-0000-0000-0000-000000000004', 'DRIVER', 'Customer on board, trip started', CURRENT_TIMESTAMP - INTERVAL '103 minutes'),
('00000000-0000-0000-0000-000000000501', 'IN_TRIP', 'COMPLETED', '00000000-0000-0000-0000-000000000004', 'DRIVER', 'Arrived at destination', CURRENT_TIMESTAMP - INTERVAL '90 minutes')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6. PAYMENT SERVICE (Database: payment_db)
-- ----------------------------------------------------------------------------
\connect payment_db;

-- Khởi tạo Ví tài xế
INSERT INTO billing.wallets
(id, driver_id, balance, currency, status, version) VALUES
('00000000-0000-0000-0000-000000000604', '00000000-0000-0000-0000-000000000004', 500000.00, 'VND', 'ACTIVE', 1),
('00000000-0000-0000-0000-000000000605', '00000000-0000-0000-0000-000000000005', 1200000.00, 'VND', 'ACTIVE', 1)
ON CONFLICT (driver_id) DO UPDATE
SET balance = EXCLUDED.balance;

-- Giao dịch mẫu
INSERT INTO billing.payments
(id, trip_id, customer_id, amount, currency, provider, status, provider_ref, idempotency_key, paid_at) VALUES
('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-000000000501',
 '00000000-0000-0000-0000-000000000003', 80000.00, 'VND', 'CASH', 'SUCCEEDED', 'SIM-HIST-001', 'PAY-TRIP-HIST-001',
 CURRENT_TIMESTAMP - INTERVAL '90 minutes')
ON CONFLICT (idempotency_key) DO NOTHING;

-- Bút toán sổ cái (Immutable Ledger Entry: -20% hoa hồng cho cuốc tiền mặt 80.000đ = -16.000đ)
INSERT INTO billing.wallet_entries
(id, wallet_id, trip_id, entry_type, amount, balance_after, reference_type, reference_id, description, status) VALUES
('00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-000000000604',
 '00000000-0000-0000-0000-000000000501', 'COMMISSION_DEBIT', 16000.00, 500000.00, 'TRIP',
 '00000000-0000-0000-0000-000000000501', 'Platform commission (20%) deducted for cash trip (Fare: 80000 VND)', 'POSTED')
ON CONFLICT (id) DO NOTHING;
