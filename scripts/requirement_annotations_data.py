# -*- coding: utf-8 -*-
"""
Database of Annotations for SRS-RHL-002 Requirements
Contains exact solution descriptions, code locations, and database/messaging references
for all 253 requirements.
"""

TRIP_ACCEPT_OFFER_CODE = (
    "backend/trip-service/.../service/impl/TripServiceImpl.java (acceptOffer); TripRepository.java"
)

ANNOTATIONS_DB = {
    # --- CONSTRAINTS (CON-01 to CON-08) ---
    "CON-01": {
        "solution": "Hệ thống phân rã thành 6 Bounded Contexts độc lập (IAM, Driver, Location, Pricing, Trip, Billing/Payment) cùng API Gateway, Eureka Discovery, Config Server và Common Library theo Domain-Driven Design.",
        "code": "backend/ (iam-service, driver-service, location-service, pricing-service, trip-service, payment-service, api-gateway)",
        "db": "PostgreSQL: 7 schemas riêng biệt (iam, driver, location, pricing, trip, billing, platform)"
    },
    "CON-02": {
        "solution": "Mỗi microservice sở hữu schema DB riêng, nghiêm cấm truy cập chéo DB. Giao tiếp liên dịch vụ qua OpenFeign (đồng bộ) có bọc Circuit Breaker Resilience4j hoặc Kafka (bất đồng bộ).",
        "code": "backend/trip-service/.../client/ (DriverClient.java, PricingClient.java, LocationClient.java)",
        "db": "Không có cross-schema foreign keys giữa các service; phân định ranh giới sở hữu dữ liệu tuyệt đối"
    },
    "CON-03": {
        "solution": "Location Service cung cấp WebSocket Handler (STOMP/WSS) để truyền tải vị trí tài xế theo thời gian thực tới Mobile Client và nhận GPS telemetry liên tục chu kỳ 3s.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java; WebSocketConfig.java",
        "db": "Redis Pub/Sub & Geospatial: driver:locations:geo, key driver:location:{id}"
    },
    "CON-04": {
        "solution": "Định vị không gian sử dụng chuẩn WGS84, kết hợp hai tầng: Bộ nhớ đệm Redis GEO (GEOADD, GEORADIUS) cho định vị thời gian thực và PostgreSQL PostGIS (GEOMETRY Point, SRID 4326) với Spatial Index GiST cho đối soát, phân tích.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java; LocationRepository.java",
        "db": "location.driver_locations (geom GEOMETRY(Point, 4326), GiST index idx_driver_locations_geom)"
    },
    "CON-05": {
        "solution": "Áp dụng mẫu hình State Machine nghiêm ngặt quản lý vòng đời chuyến xe: DRAFT -> SEARCHING -> OFFERED -> ACCEPTED -> ARRIVING -> IN_PROGRESS -> COMPLETED hoặc CANCELLED. Mọi chuyển đổi trạng thái không hợp lệ đều bị bác bỏ bằng BusinessException.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (updateTripStatus); TripStatus.java",
        "db": "trip.trips (status VARCHAR(30)); trip.trip_status_history ghi vết lịch sử actor, thời gian, lý do"
    },
    "CON-06": {
        "solution": "Đảm bảo tính nguyên tử tuyệt đối khi gán tài xế: Sử dụng Redis Distributed Lock (khóa tài xế trong 15s) kết hợp Partial Unique Index tại tầng Database PostgreSQL để ngăn chặn triệt để tình trạng Race Condition nhiều cuốc gán 1 tài xế.",
        "code": TRIP_ACCEPT_OFFER_CODE,
        "db": "trip.trips (Partial Unique Index: idx_trips_driver_active_unique WHERE status IN ('ACCEPTED', 'ARRIVING', 'IN_PROGRESS'))"
    },
    "CON-07": {
        "solution": "Khi chuyến hoàn thành, Trip Service phát sự kiện TripCompletedEvent vào Apache Kafka topic 'trip.completed' qua Transactional Outbox. Payment Service lắng nghe và thực hiện thanh toán bất đồng bộ, đảm bảo tính nhất quán cuối cùng (Eventual Consistency).",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java; backend/payment-service/.../event/TripCompletedConsumer.java",
        "db": "Kafka Topic: 'trip.completed'; platform.outbox_events (Transactional Outbox Pattern)"
    },
    "CON-08": {
        "solution": "Tuân thủ tiêu chuẩn bảo mật PCI-DSS: Hệ thống không lưu trữ bất kỳ số thẻ (PAN), CVV/CVC nào trong Database. Chỉ lưu trữ Token an toàn (tokenized card ID) và Payment Gateway Transaction Reference.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java; PaymentRequest.java",
        "db": "billing.payments (payment_method, transaction_reference, không chứa thông tin thẻ nhạy cảm)"
    },

    # --- BUSINESS RULES (BR-001 to BR-015) ---
    "BR-001": {
        "solution": "Kiểm tra tính hợp lệ toàn diện của tài xế trước khi cho phép ONLINE: Trạng thái hồ sơ APPROVED, CCCD/GPLX/Bảo hiểm còn hạn, phương tiện hoạt động. Từ chối bật AVAILABLE nếu thiếu điều kiện.",
        "code": "backend/driver-service/.../service/impl/DriverProfileServiceImpl.java (updateStatus); DriverStatus.java",
        "db": "driver.driver_profiles (status = 'APPROVED', is_active = true); driver.vehicles (status = 'ACTIVE')"
    },
    "BR-002": {
        "solution": "Ngăn chặn một tài xế nhận 2 cuốc xe đồng thời bằng kiểm tra logic truy vấn chuyến đang hoạt động và khóa chặn tầng DB bằng PostgreSQL Partial Unique Index.",
        "code": TRIP_ACCEPT_OFFER_CODE,
        "db": "trip.trips (Partial Index: idx_trips_driver_active_unique: 1 tài xế chỉ có tối đa 1 chuyến ACCEPTED/ARRIVING/IN_PROGRESS)"
    },
    "BR-003": {
        "solution": "Mỗi chuyến chỉ có tối đa một tài xế được gán. Khi tài xế đầu tiên chấp nhận offer thành công, offer chuyển sang ACCEPTED, đồng thời đóng/hủy tất cả offers khác của chuyến đó.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (acceptOffer); DriverOfferRepository.java",
        "db": "trip.driver_offers (status = 'ACCEPTED' / 'EXPIRED'); trip.trips (driver_id NOT NULL sau khi accept)"
    },
    "BR-004": {
        "solution": "Chỉ lấy các tọa độ tài xế có độ mới <= 30 giây (kiểm tra TTL trong Redis và timestamp GPS) và độ chính xác accuracyMeters <= 50m khi chạy thuật toán tìm tài xế gần điểm đón.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (getNearbyDrivers); LocationDto.java",
        "db": "Redis key: driver:location:{id} (TTL 60s); location.driver_locations (updated_at >= NOW() - INTERVAL '30 seconds')"
    },
    "BR-005": {
        "solution": "Kiểm tra Fare Quote khi khách hàng đặt xe: Quote phải thuộc đúng customer_id, trạng thái còn hiệu lực (expires_at > NOW(), mặc định hiệu lực 10 phút) và không được tái sử dụng nếu đã tạo chuyến.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (validateQuote); backend/trip-service/.../service/impl/TripServiceImpl.java",
        "db": "pricing.fare_quotes (customer_id, expires_at, is_used BOOLEAN DEFAULT false)"
    },
    "BR-006": {
        "solution": "Hệ số giá động (Surge Multiplier) được tính toán dựa trên tỷ lệ Nhu cầu/Nguồn cung (Demand/Supply ratio) theo từng khu vực H3/Geohash. Hệ số này bắt buộc trả về cho khách xem và xác nhận trước khi bấm đặt xe.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (calculateFare); FareQuoteResponse.java",
        "db": "pricing.fare_quotes (surge_multiplier NUMERIC(3,2), surge_reason VARCHAR(255))"
    },
    "BR-007": {
        "solution": "Toàn bộ thông tin báo giá (base fare, distance fare, time fare, surge multiplier, final fare) được snapshot nguyên vẹn vào bản ghi chuyến xe tại thời điểm tạo để tránh bị ảnh hưởng nếu bảng giá hệ thống thay đổi sau đó.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (createTripFromQuote); Trip.java",
        "db": "trip.trips (quote_snapshot JSONB hoặc các cột fare_amount, distance_km, estimated_duration_minutes)"
    },
    "BR-008": {
        "solution": "Kiểm soát quyền chuyển trạng thái theo actor: Khách hàng chỉ được CANCEL khi chưa bắt đầu; Tài xế chỉ được ARRIVING, START_TRIP, COMPLETE_TRIP; Admin có quyền CANCEL cưỡng chế khi có sự cố.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (updateTripStatus); SecurityContextUtil.java",
        "db": "trip.trip_status_history (previous_status, new_status, changed_by, actor_role, reason)"
    },
    "BR-009": {
        "solution": "Xử lý hoàn tất chuyến xe nguyên tử Idempotent: Khi tài xế bấm kết thúc cuốc, trạng thái chuyển sang COMPLETED duy nhất một lần; phát sự kiện hoàn thành kích hoạt thanh toán một lần, chống duplicate webhook/request.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (completeTrip); TripCompletedConsumer.java",
        "db": "trip.trips (status = 'COMPLETED', completed_at = NOW()); platform.idempotency_keys"
    },
    "BR-010": {
        "solution": "Ràng buộc kiểm tra số tiền hoàn (Refund): Tổng các khoản hoàn tiền tích lũy cho một chuyến xe không được vượt quá số tiền thực tế khách đã thanh toán ban đầu.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (processRefund); RefundRequest.java",
        "db": "billing.refunds (amount <= payment.amount; Check Constraint / Service check sum(refunds) <= payment.amount)"
    },
    "BR-011": {
        "solution": "Ví tài xế thiết kế theo mô hình Kế toán kép Bất biến (Immutable Financial Ledger). Cấm lệnh UPDATE số dư trực tiếp. Mọi biến động tiền đều phải tạo bản ghi bút toán Wallet Entry (CREDIT hoặc DEBIT) kèm tham chiếu nguồn gốc.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (creditDriverWallet, debitDriverWallet); WalletEntry.java",
        "db": "billing.wallets (balance); billing.wallet_entries (entry_type: CREDIT/DEBIT, amount, balance_after, reference_id)"
    },
    "BR-012": {
        "solution": "Thu nhập ròng của tài xế được tính tự động khi chuyến hoàn tất: Số tiền thực nhận = Tổng cước phí chuyến xe - Phí nền tảng (Platform Commission, ví dụ 20%) + Tiền tip của khách.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (calculateDriverEarnings); TripCompletedConsumer.java",
        "db": "billing.wallet_entries (description: 'Trip earnings after platform fee', amount: fare * (1 - commission_rate))"
    },
    "BR-013": {
        "solution": "Bảo vệ vị trí riêng tư: Tọa độ chính xác của tài xế chỉ được truyền tới khách hàng khi chuyến đang ở trạng thái ACCEPTED, ARRIVING hoặc IN_PROGRESS. Khi cuốc xe kết thúc hoặc hủy, kênh chia sẻ tọa độ lập tức bị ngắt.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java; LocationWebSocketHandler.java",
        "db": "Redis Pub/Sub channel: trip:tracking:{tripId}; kiểm tra trạng thái trip.trips trước khi broadcast"
    },
    "BR-014": {
        "solution": "Mọi thao tác quản trị nhạy cảm (duyệt tài xế, điều chỉnh bảng giá, cộng trừ ví thủ công, hủy chuyến đặc biệt) đều được ghi nhận vào bảng Audit Log tập trung, không thể chỉnh sửa.",
        "code": "backend/driver-service/.../service/impl/DriverProfileServiceImpl.java; backend/common-library/.../audit/AuditLog.java",
        "db": "iam.audit_logs, driver.driver_audit_logs, platform.audit_logs (actor_id, action, old_data, new_data, created_at)"
    },
    "BR-015": {
        "solution": "Chống trùng lặp yêu cầu và sự kiện (Idempotency): Sử dụng Header 'Idempotency-Key' kết hợp lưu vết trong bảng platform.idempotency_keys. Nếu nhận lại cùng một key, trả về kết quả đã lưu thay vì tạo bản ghi mới.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java; backend/payment-service/.../service/impl/PaymentServiceImpl.java",
        "db": "platform.idempotency_keys (key_value PRIMARY KEY, response_payload, created_at, expires_at)"
    },

    # --- IAM REQUIREMENTS (FR-IAM-001 to FR-IAM-011) ---
    "FR-IAM-001": {
        "solution": "Cung cấp API REST đăng ký tài khoản cho Khách hàng và Tài xế với đầy đủ thông tin: họ tên, số điện thoại, email, mật khẩu và vai trò (CUSTOMER / DRIVER).",
        "code": "backend/iam-service/.../controller/AuthController.java (register); AuthServiceImpl.java (register); RegisterRequest.java",
        "db": "iam.users (id, phone_number, email, password_hash, status); iam.user_roles (user_id, role_id)"
    },
    "FR-IAM-002": {
        "solution": "Validate định dạng email chuẩn RFC 5322, số điện thoại chuẩn Việt Nam (bắt đầu bằng 0 hoặc +84 với 10 chữ số). Kiểm tra tính duy nhất (Unique) trước khi lưu vào DB.",
        "code": "backend/iam-service/.../dto/request/RegisterRequest.java (@Pattern phone regex, @Email); AuthServiceImpl.java",
        "db": "iam.users (Unique Constraint: uq_users_email, uq_users_phone_number)"
    },
    "FR-IAM-003": {
        "solution": "Mật khẩu tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt. Mã hóa an toàn bằng BCryptPasswordEncoder với Work Factor = 12 trước khi lưu trữ.",
        "code": "backend/iam-service/.../config/SecurityConfig.java (passwordEncoder); PasswordValidator.java",
        "db": "iam.users (cột password_hash VARCHAR(255), tuyệt đối không lưu plain text)"
    },
    "FR-IAM-004": {
        "solution": "Hỗ trợ kích hoạt tài khoản và xác minh thông qua mã OTP số 6 chữ số gửi qua SMS/Email hoặc liên kết kích hoạt an toàn có thời hạn 15 phút.",
        "code": "backend/iam-service/.../service/impl/AuthServiceImpl.java (verifyAccount, sendVerificationCode)",
        "db": "iam.users (is_verified BOOLEAN, verification_token VARCHAR(100), verification_expires_at TIMESTAMP)"
    },
    "FR-IAM-005": {
        "solution": "API Đăng nhập xác thực thông tin tài khoản, cấp phát cặp JWT Token: Access Token (thời hạn 15 phút) chứa Claims vai trò và Refresh Token (thời hạn 7 ngày).",
        "code": "backend/iam-service/.../controller/AuthController.java (login); AuthServiceImpl.java; JwtUtil.java",
        "db": "iam.refresh_tokens (token_hash, user_id, expires_at, revoked)"
    },
    "FR-IAM-006": {
        "solution": "Áp dụng cơ chế giới hạn đăng nhập sai (Brute-force Protection): Sau 5 lần nhập sai mật khẩu liên tiếp, tài khoản bị tạm khóa trong 15 phút. Thông báo lỗi chung không tiết lộ email/SĐT có tồn tại hay không.",
        "code": "backend/iam-service/.../service/impl/AuthServiceImpl.java (login); LoginAttemptService.java",
        "db": "iam.users (failed_login_attempts INT, locked_until TIMESTAMP)"
    },
    "FR-IAM-007": {
        "solution": "Cung cấp API Refresh Token để cấp lại Access Token mới, API Logout thu hồi phiên đăng nhập và API Quên mật khẩu đặt lại mật khẩu an toàn.",
        "code": "backend/iam-service/.../controller/AuthController.java (refreshToken, logout, resetPassword); AuthServiceImpl.java",
        "db": "iam.refresh_tokens (revoked = true khi logout hoặc token rotation)"
    },
    "FR-IAM-008": {
        "solution": "API xem và cập nhật hồ sơ cá nhân của người dùng: tên hiển thị, ảnh đại diện đại diện, email liên hệ. Chặn người dùng tự thay đổi số điện thoại và vai trò.",
        "code": "backend/iam-service/.../controller/UserController.java (getProfile, updateProfile); UserServiceImpl.java",
        "db": "iam.users (full_name, avatar_url, updated_at)"
    },
    "FR-IAM-009": {
        "solution": "Mô hình phân quyền dựa trên vai trò (RBAC) chặt chẽ với các vai trò chuẩn: ROLE_CUSTOMER, ROLE_DRIVER, ROLE_ADMIN, ROLE_REVIEWER, ROLE_OPERATOR.",
        "code": "backend/iam-service/.../entity/Role.java; backend/common-library/.../security/SecurityConstants.java",
        "db": "iam.roles (id, role_name); iam.user_roles (user_id, role_id)"
    },
    "FR-IAM-010": {
        "solution": "Mọi yêu cầu gọi vào microservices đều phải đi qua API Gateway kiểm tra JWT Filter, trích xuất Claims xác thực vai trò và inject header X-User-Id, X-User-Role cho các service sau.",
        "code": "backend/api-gateway/.../filter/JwtAuthenticationGatewayFilter.java; SecurityConfig.java",
        "db": "Token Stateless; Blacklist token lưu trên Redis nếu cần thu hồi khẩn cấp"
    },
    "FR-IAM-011": {
        "solution": "Mọi thay đổi nhạy cảm về vai trò, khóa tài khoản hoặc reset mật khẩu của người dùng đều tự động kích hoạt tạo sự kiện Audit Log.",
        "code": "backend/iam-service/.../service/impl/UserServiceImpl.java (changeRole, lockUser); AuditLogServiceImpl.java",
        "db": "iam.audit_logs (user_id, action, details, ip_address, created_at)"
    },

    # --- DRIVER & VEHICLE REQUIREMENTS (FR-DRV-001 to FR-DRV-014) ---
    "FR-DRV-001": {
        "solution": "Tài xế đăng ký hồ sơ cung cấp số bằng lái xe (GPLX), ngày cấp, ngày hết hạn, số CCCD/hộ chiếu và thông tin liên hệ.",
        "code": "backend/driver-service/.../controller/DriverController.java (createProfile); DriverProfileServiceImpl.java",
        "db": "driver.driver_profiles (user_id, license_number, national_id, status = 'DRAFT')"
    },
    "FR-DRV-002": {
        "solution": "Validate tính hợp lệ của hồ sơ tài xế: số bằng lái, CCCD không trùng lặp, ngày hết hạn phải sau ngày hiện tại ít nhất 30 ngày.",
        "code": "backend/driver-service/.../service/impl/DriverProfileServiceImpl.java; DriverProfileRequest.java",
        "db": "driver.driver_profiles (Unique Constraint: uq_driver_license_number, uq_driver_national_id)"
    },
    "FR-DRV-003": {
        "solution": "Quản lý vòng đời hồ sơ tài xế với máy trạng thái: DRAFT -> SUBMITTED -> APPROVED / REJECTED -> SUSPENDED.",
        "code": "backend/driver-service/.../entity/DriverProfile.java (DriverStatus Enum); DriverProfileServiceImpl.java",
        "db": "driver.driver_profiles (status VARCHAR(30) DEFAULT 'DRAFT')"
    },
    "FR-DRV-004": {
        "solution": "API cho phép tài xế nộp hồ sơ (Submit) sau khi đã nhập đủ thông tin và upload đầy đủ tài liệu giấy tờ hợp lệ.",
        "code": "backend/driver-service/.../controller/DriverController.java (submitProfile); DriverProfileServiceImpl.java",
        "db": "driver.driver_profiles (status = 'SUBMITTED', submitted_at = NOW())"
    },
    "FR-DRV-005": {
        "solution": "Admin/Reviewer kiểm tra hồ sơ, duyệt (Approve) hoặc từ chối (Reject) kèm lý do chi tiết gửi thông báo cho tài xế.",
        "code": "backend/driver-service/.../controller/AdminDriverController.java (reviewProfile); DriverProfileServiceImpl.java",
        "db": "driver.driver_profiles (status = 'APPROVED'/'REJECTED', rejection_reason, reviewed_by, reviewed_at)"
    },
    "FR-DRV-006": {
        "solution": "Ghi nhận quyết định phê duyệt tài xế vào bảng Audit Log: người duyệt, thời gian duyệt, tài liệu được duyệt, lý do từ chối (nếu có).",
        "code": "backend/driver-service/.../service/impl/DriverProfileServiceImpl.java; DriverAuditLog.java",
        "db": "driver.driver_audit_logs (driver_id, action, reviewer_id, notes, created_at)"
    },
    "FR-DRV-007": {
        "solution": "Quản lý thông tin phương tiện của tài xế: loại xe (MOTORBIKE, CAR_4_SEATS, CAR_7_SEATS, TRUCK), biển số, hãng sản xuất, mẫu xe, màu sắc và năm sản xuất.",
        "code": "backend/driver-service/.../controller/VehicleController.java; VehicleServiceImpl.java; Vehicle.java",
        "db": "driver.vehicles (id, driver_id, vehicle_type, license_plate, brand, model, color, year, status)"
    },
    "FR-DRV-008": {
        "solution": "Chỉ những tài xế có hồ sơ trạng thái APPROVED và phương tiện ACTIVE mới được phép nhận cuốc xe trong hệ thống.",
        "code": "backend/driver-service/.../service/impl/DriverProfileServiceImpl.java (isEligibleToDrive); DriverClient.java",
        "db": "driver.driver_profiles (status = 'APPROVED'); driver.vehicles (status = 'ACTIVE')"
    },
    "FR-DRV-009": {
        "solution": "Quản lý trạng thái hoạt động theo thời gian thực của tài xế: OFFLINE, AVAILABLE, BUSY (đang chở khách hoặc nhận offer).",
        "code": "backend/driver-service/.../service/impl/DriverProfileServiceImpl.java (updateAvailability); DriverAvailability.java",
        "db": "driver.driver_profiles (availability_status VARCHAR(20) DEFAULT 'OFFLINE')"
    },
    "FR-DRV-010": {
        "solution": "API cho tài xế bật Online (AVAILABLE) hoặc tắt Offline khi không muốn nhận chuyến xe mới.",
        "code": "backend/driver-service/.../controller/DriverController.java (setAvailability); DriverProfileServiceImpl.java",
        "db": "driver.driver_profiles (availability_status = 'AVAILABLE'/'OFFLINE', last_online_at TIMESTAMP)"
    },
    "FR-DRV-011": {
        "solution": "Chặn tài xế chuyển trạng thái Offline khi đang trong chuyến xe hoạt động (ASSIGNED, ARRIVING, IN_PROGRESS).",
        "code": "backend/driver-service/.../service/impl/DriverProfileServiceImpl.java; TripClient.java",
        "db": "Kiểm tra trip.trips có chuyến nào chưa hoàn tất của driver_id hay không trước khi đổi trạng thái"
    },
    "FR-DRV-012": {
        "solution": "Khi một cuốc xe được dispatch tới tài xế (offer tạo ra), trạng thái khả dụng của tài xế tạm chuyển sang BUSY để không nhận offer khác.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (dispatchTripToDrivers); DriverClient.java",
        "db": "Redis key: driver:availability:{driverId} = 'BUSY'; TTL = 15s (thời gian chờ phản hồi offer)"
    },
    "FR-DRV-013": {
        "solution": "Khi offer bị từ chối/hết hạn hoặc chuyến xe hoàn tất/bị hủy, hệ thống tự động hoàn trả trạng thái tài xế về AVAILABLE.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (handleOfferTimeout, completeTrip)",
        "db": "driver.driver_profiles (availability_status = 'AVAILABLE'); Redis key xóa hoặc set 'AVAILABLE'"
    },
    "FR-DRV-014": {
        "solution": "Hệ thống tự động phát hiện mất tín hiệu: Nếu tài xế không gửi GPS cập nhật quá 60 giây, tự động chuyển trạng thái tài xế sang OFFLINE để tránh ghép cuốc hỏng.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (Heartbeat / TTL monitor scheduler)",
        "db": "Redis TTL trên key driver:location:{id} hết hạn; cập nhật driver.driver_profiles (availability_status = 'OFFLINE')"
    },

    # --- LOCATION & TELEMETRY REQUIREMENTS (FR-LOC-001 to FR-LOC-014) ---
    "FR-LOC-001": {
        "solution": "API tiếp nhận dữ liệu telemetry từ tài xế: kinh độ (lng), vĩ độ (lat), độ chính xác (accuracy), góc hướng (heading), vận tốc (speed) và timestamp.",
        "code": "backend/location-service/.../controller/LocationController.java (updateLocation); LocationServiceImpl.java",
        "db": "location.driver_locations; Redis GEO key 'driver:locations:geo'"
    },
    "FR-LOC-002": {
        "solution": "Validate phạm vi tọa độ hợp lệ chuẩn WGS84: Latitude trong khoảng [-90, +90], Longitude trong khoảng [-180, +180]. Loại bỏ tọa độ ngoại lai (noise).",
        "code": "backend/location-service/.../dto/LocationDto.java (@Min, @Max); LocationServiceImpl.java",
        "db": "location.driver_locations (Check Constraint: latitude BETWEEN -90 AND 90, longitude BETWEEN -180 AND 180)"
    },
    "FR-LOC-003": {
        "solution": "Kiểm tra độ chính xác GPS: Chỉ chấp nhận tọa độ có accuracy <= 50 mét; gắn nhãn cảnh báo nếu accuracy kém và loại khỏi dữ liệu matching.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (validateTelemetry); LocationDto.java",
        "db": "location.driver_locations (accuracy_meters NUMERIC(6,2))"
    },
    "FR-LOC-004": {
        "solution": "Cập nhật vị trí mới nhất của tài xế vào Redis Cache (GEO và Hash) với độ trễ thấp (< 50ms) để phục vụ tra cứu tức thì.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (cacheLatestLocation); RedisTemplate",
        "db": "Redis GEO: GEOADD driver:locations:geo lng lat driverId; Redis Hash: driver:location:{id}"
    },
    "FR-LOC-005": {
        "solution": "Đồng bộ bất đồng bộ lịch sử vị trí vào cơ sở dữ liệu PostgreSQL PostGIS để phục vụ lưu trữ hành trình, đối soát và giải quyết khiếu nại.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (saveTelemetryHistory); LocationRepository.java",
        "db": "location.driver_telemetry_history (driver_id, geom GEOMETRY(Point, 4326), speed, heading, recorded_at)"
    },
    "FR-LOC-006": {
        "solution": "Truy vấn tìm kiếm danh sách các tài xế khả dụng gần tọa độ điểm đón của khách hàng trong bán kính R (mặc định 3-5 km).",
        "code": "backend/location-service/.../controller/LocationController.java (getNearbyDrivers); LocationServiceImpl.java",
        "db": "Redis: GEORADIUSBYMEMBER / GEORADIUS driver:locations:geo lng lat R km WITHDIST WITHCOORD"
    },
    "FR-LOC-007": {
        "solution": "Lọc danh sách tài xế theo loại phương tiện được khách hàng yêu cầu (XE_MAY, O_TO_4_CHO, O_TO_7_CHO, GIAO_HANG) trước khi trả về kết quả matching.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (getNearbyDriversByVehicleType)",
        "db": "Kết hợp Redis GEO và OpenFeign gọi driver-service lấy vehicle_type tương ứng"
    },
    "FR-LOC-008": {
        "solution": "Ước tính khoảng cách đường chim bay (Haversine Formula) hoặc khoảng cách theo mạng lưới đường bộ (Road Network) giữa tài xế và điểm đón.",
        "code": "backend/location-service/.../util/GeoUtils.java (calculateHaversineDistance); LocationServiceImpl.java",
        "db": "PostGIS: ST_Distance(geom1, geom2) hoặc hàm Haversine thuần toán học trong GeoUtils"
    },
    "FR-LOC-009": {
        "solution": "Thời gian tới điểm đón ước tính (ETA) được tính toán dựa trên khoảng cách và vận tốc trung bình theo khung giờ đô thị (25-30 km/h).",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (estimateEta); EtaResponse.java",
        "db": "location.driver_locations; tính toán in-memory thời gian ETA (phút)"
    },
    "FR-LOC-010": {
        "solution": "Cung cấp API cho khách hàng theo dõi vị trí trực tiếp của tài xế đang thực hiện chuyến xe của mình theo thời gian thực.",
        "code": "backend/location-service/.../controller/LocationController.java (getDriverCurrentLocationForTrip)",
        "db": "Redis key: driver:location:{driverId}; xác thực quyền qua trip_id"
    },
    "FR-LOC-011": {
        "solution": "Bảo vệ thông tin định vị: Chỉ cho phép khách hàng xem vị trí của tài xế sau khi tài xế đã chấp nhận cuốc xe (ACCEPTED) cho đến khi chuyến kết thúc.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java; TripClient.java",
        "db": "Kiểm tra trip.trips (customer_id = current_user AND driver_id = requested_driver AND status IN ('ACCEPTED', 'ARRIVING', 'IN_PROGRESS'))"
    },
    "FR-LOC-012": {
        "solution": "Phát hiện tài xế đi chệch tuyến đường dự kiến (Route Deviation Detection) và cảnh báo bất thường trong quá trình di chuyển.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (checkRouteDeviation)",
        "db": "location.driver_telemetry_history; so khớp tọa độ hiện tại với bounding box tuyến đường"
    },
    "FR-LOC-013": {
        "solution": "Tự động làm sạch dữ liệu định vị tạm thời trong Redis: Sử dụng cơ chế TTL (Time-To-Live = 60s) để loại bỏ tự động tọa độ tài xế không còn cập nhật.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java; RedisConfig.java",
        "db": "Redis EXPIRE driver:location:{id} 60"
    },
    "FR-LOC-014": {
        "solution": "Nén và lưu trữ dữ liệu lịch sử hành trình: Định kỳ di chuyển telemetry cũ sang bảng lưu trữ dạng Time-series hoặc phân vùng Partition để tối ưu hiệu năng DB.",
        "code": "backend/location-service/.../repository/LocationRepository.java; ScheduledLocationArchiver.java",
        "db": "location.driver_telemetry_history (partitioned by month/range theo timestamp)"
    },

    # --- PRICING & SURGE REQUIREMENTS (FR-PRI-001 to FR-PRI-018) ---
    "FR-PRI-001": {
        "solution": "Định cấu hình bảng giá cước cơ sở (Pricing Rules) linh hoạt theo loại dịch vụ (RIDE, DELIVERY), khu vực địa lý và khung giờ.",
        "code": "backend/pricing-service/.../controller/PricingController.java; PricingServiceImpl.java; PricingRule.java",
        "db": "pricing.pricing_rules (service_type, city_code, base_fare, price_per_km, price_per_minute)"
    },
    "FR-PRI-002": {
        "solution": "Tính giá mở cửa (Base Fare): Cước phí cố định cho km đầu tiên theo từng loại phương tiện (Xe máy: 12,000 VNĐ; Xe 4 chỗ: 20,000 VNĐ).",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (calculateFare); PricingRuleRepository.java",
        "db": "pricing.pricing_rules (base_fare NUMERIC(12,2), base_distance_km NUMERIC(5,2))"
    },
    "FR-PRI-003": {
        "solution": "Tính cước theo khoảng cách thực tế (Distance Fare): Giá mỗi km nhân với tổng quãng đường di chuyển vượt quá khoảng cách cơ sở.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java; FareCalculationEngine.java",
        "db": "pricing.pricing_rules (price_per_km NUMERIC(12,2))"
    },
    "FR-PRI-004": {
        "solution": "Tính cước theo thời gian di chuyển dự kiến (Duration/Time Fare) nhằm bù đắp cho tài xế khi gặp tắc đường hoặc thời gian di chuyển kéo dài.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java; FareCalculationEngine.java",
        "db": "pricing.pricing_rules (price_per_minute NUMERIC(12,2))"
    },
    "FR-PRI-005": {
        "solution": "Áp dụng giá cước giờ cao điểm / đêm muộn: Phụ phí cố định hoặc hệ số nhân vào ban đêm (22h00 - 05h00) và giờ cao điểm sáng/chiều.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (applyTimeSurcharges)",
        "db": "pricing.pricing_rules (night_surcharge NUMERIC(12,2), peak_hour_multiplier NUMERIC(3,2))"
    },
    "FR-PRI-006": {
        "solution": "Thuật toán tính giá động Surge Pricing: Tự động điều chỉnh hệ số nhân giá (1.0x - 2.5x) dựa trên tỷ lệ Nhu cầu đặt xe (Demand) và Số tài xế trực tuyến (Supply) tại khu vực.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (calculateSurgeMultiplier)",
        "db": "pricing.fare_quotes (surge_multiplier NUMERIC(3,2)); Redis counter nhu cầu/cung"
    },
    "FR-PRI-007": {
        "solution": "Giới hạn trần giá động (Surge Capping): Khống chế hệ số nhân giá động không vượt quá ngưỡng tối đa cho phép (ví dụ 2.5x) để bảo vệ quyền lợi người tiêu dùng.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (MAX_SURGE_MULTIPLIER = 2.5)",
        "db": "pricing.pricing_rules (max_surge_multiplier NUMERIC(3,2) DEFAULT 2.50)"
    },
    "FR-PRI-008": {
        "solution": "Hỗ trợ phụ phí đặc biệt: Phụ phí thời tiết xấu (mưa bão), phụ phí ngày lễ tết, phụ phí hàng cồng kềnh đối với dịch vụ giao hàng.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (applySpecialSurcharges)",
        "db": "pricing.fare_quotes (weather_surcharge NUMERIC(12,2), holiday_surcharge NUMERIC(12,2))"
    },
    "FR-PRI-009": {
        "solution": "API Báo giá chuyến đi (Fare Quote) trước khi đặt xe: Trả về chi tiết cước ước tính, khoảng cách km, thời gian di chuyển và hạn hiệu lực của báo giá.",
        "code": "backend/pricing-service/.../controller/PricingController.java (createQuote); FareQuoteResponse.java",
        "db": "pricing.fare_quotes (id, customer_id, total_fare, expires_at, created_at)"
    },
    "FR-PRI-010": {
        "solution": "Báo giá (Fare Quote) có hiệu lực cố định 10 phút. Quá thời gian này khách hàng phải yêu cầu báo giá mới.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (QUOTE_TTL_MINUTES = 10)",
        "db": "pricing.fare_quotes (expires_at = created_at + INTERVAL '10 minutes')"
    },
    "FR-PRI-011": {
        "solution": "Xác thực báo giá khi tạo chuyến: Trip Service gọi Pricing Service xác nhận báo giá còn hạn và chưa từng được sử dụng trước khi tạo chuyến xe.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (validateAndLockQuote); PricingClient.java",
        "db": "pricing.fare_quotes (is_used = true sau khi trip được tạo thành công)"
    },
    "FR-PRI-012": {
        "solution": "Lưu snapshot chi tiết các thành phần cấu thành giá trong báo giá (Base, Distance, Time, Surge, Tolls) để phục vụ giải trình minh bạch.",
        "code": "backend/pricing-service/.../entity/FareQuote.java; FareBreakdownDto.java",
        "db": "pricing.fare_quotes (base_fare, distance_fare, duration_fare, surge_multiplier, total_fare)"
    },
    "FR-PRI-013": {
        "solution": "Hỗ trợ áp dụng mã giảm giá (Promotion / Discount): Kiểm tra điều kiện voucher, trừ tiền theo tỷ lệ % hoặc số tiền cố định và lưu số tiền giảm giá.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (applyPromotionCode)",
        "db": "pricing.fare_quotes (promotion_code VARCHAR(50), discount_amount NUMERIC(12,2))"
    },
    "FR-PRI-014": {
        "solution": "Tính cước lộ trình nhiều điểm dừng (Multi-stop Pricing): Tổng hợp khoảng cách và thời gian qua các trạm dừng để tính giá chính xác.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (calculateMultiStopFare)",
        "db": "pricing.fare_quotes; liên kết thông tin các điểm dừng trong mảng stops"
    },
    "FR-PRI-015": {
        "solution": "Tính phí hủy chuyến (Cancellation Fee) nếu khách hàng hoặc tài xế hủy chuyến sau thời gian miễn phí cho phép (ví dụ sau 3 phút kể từ khi nhận cuốc).",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (calculateCancellationFee)",
        "db": "pricing.pricing_rules (cancellation_fee NUMERIC(12,2), free_cancellation_minutes INT)"
    },
    "FR-PRI-016": {
        "solution": "Bắt buộc sử dụng kiểu dữ liệu số thực có độ chính xác tuyệt đối (BigDecimal / NUMERIC(12,2)), nghiêm cấm kiểu float/double để tránh sai số tiền tệ tài chính.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (BigDecimal arithmetic với RoundingMode.HALF_UP)",
        "db": "pricing.fare_quotes, pricing.pricing_rules (tất cả các cột tiền tệ là NUMERIC(12,2))"
    },
    "FR-PRI-017": {
        "solution": "Làm tròn tiền cước theo quy chuẩn tiền tệ Việt Nam Đồng (làm tròn đến hàng nghìn đồng hoặc giữ chẵn xu tùy cấu hình hệ thống).",
        "code": "backend/pricing-service/.../util/FareRoundingUtil.java (roundFareToThousands); PricingServiceImpl.java",
        "db": "pricing.fare_quotes (total_fare = ROUND(calculated_fare, -3))"
    },
    "FR-PRI-018": {
        "solution": "API cho Quản trị viên cập nhật cấu hình bảng giá và hệ số giá theo từng thành phố/vùng mà không cần khởi động lại hệ thống.",
        "code": "backend/pricing-service/.../controller/AdminPricingController.java (updateRule); PricingServiceImpl.java",
        "db": "pricing.pricing_rules (updated_at = NOW(), updated_by = admin_id)"
    },

    # --- TRIP MANAGEMENT REQUIREMENTS (FR-TRIP-001 to FR-TRIP-015) ---
    "FR-TRIP-001": {
        "solution": "API cho Khách hàng tạo chuyến xe mới dựa trên Fare Quote hợp lệ: ghi nhận điểm đón (Pickup), điểm đến (Dropoff), loại dịch vụ và phương thức thanh toán.",
        "code": "backend/trip-service/.../controller/TripController.java (createTrip); TripServiceImpl.java; CreateTripRequest.java",
        "db": "trip.trips (id, customer_id, service_type, pickup_address, dropoff_address, fare_amount, status = 'DRAFT')"
    },
    "FR-TRIP-002": {
        "solution": "Validate dữ liệu chuyến đi: Điểm đón và điểm trả phải có tọa độ hợp lệ, khoảng cách di chuyển tối thiểu 100m, tối đa 300km.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (validateTripLocations); LocationDto.java",
        "db": "trip.trips (pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, distance_km)"
    },
    "FR-TRIP-003": {
        "solution": "Hỗ trợ chuyến xe nhiều điểm dừng (Trip Stops): Lưu thứ tự các điểm dừng trung gian (Sequence 1, 2, 3...) cùng thông tin người liên hệ tại mỗi điểm.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (createTripStops); TripStop.java",
        "db": "trip.trip_stops (id, trip_id, stop_sequence, address, latitude, longitude, status)"
    },
    "FR-TRIP-004": {
        "solution": "Khởi động quy trình tìm kiếm tài xế (Dispatch): Chuyển trạng thái chuyến từ DRAFT sang SEARCHING và gọi Location Service quét tìm tài xế gần nhất.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (startMatching); LocationClient.java",
        "db": "trip.trips (status = 'SEARCHING', search_started_at = NOW())"
    },
    "FR-TRIP-005": {
        "solution": "Tạo Offer gửi tới tài xế (Driver Offer): Lưu bản ghi offer với hạn phản hồi cố định (ví dụ 15 giây), chuyển trạng thái chuyến sang OFFERED.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (createDriverOffer); DriverOffer.java",
        "db": "trip.driver_offers (id, trip_id, driver_id, status = 'PENDING', expires_at = NOW() + 15s)"
    },
    "FR-TRIP-006": {
        "solution": "Tài xế chấp nhận cuốc xe (Accept Offer): Thực hiện nguyên tử bằng Redis Distributed Lock, gán driver_id vào chuyến và chuyển trạng thái chuyến sang ACCEPTED.",
        "code": TRIP_ACCEPT_OFFER_CODE,
        "db": "trip.trips (driver_id, status = 'ACCEPTED', accepted_at = NOW()); trip.driver_offers (status = 'ACCEPTED')"
    },
    "FR-TRIP-007": {
        "solution": "Tài xế từ chối cuốc xe (Reject Offer): Cập nhật offer sang REJECTED, mở lại chuyến về SEARCHING để gửi offer cho tài xế tiếp theo trong danh sách ứng viên.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (rejectOffer); DriverOfferRepository.java",
        "db": "trip.driver_offers (status = 'REJECTED'); trip.trips (status = 'SEARCHING')"
    },
    "FR-TRIP-008": {
        "solution": "Xử lý hết hạn Offer (Offer Timeout): Cơ chế Timeout Scheduler tự động phát hiện offer không được phản hồi sau 15 giây, đánh dấu EXPIRED và chuyển sang tài xế kế tiếp.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (handleExpiredOffers); DriverOfferRepository.java",
        "db": "trip.driver_offers (status = 'EXPIRED'); trip.trips (status = 'SEARCHING')"
    },
    "FR-TRIP-009": {
        "solution": "Tài xế báo đã đến điểm đón (Arrived at Pickup): Chuyển trạng thái chuyến sang ARRIVING / ARRIVED, thông báo cho khách hàng chuẩn bị lên xe.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (driverArrived); TripController.java",
        "db": "trip.trips (status = 'ARRIVING', arrived_at = NOW()); trip.trip_status_history"
    },
    "FR-TRIP-010": {
        "solution": "Bắt đầu hành trình (Start Trip): Tài xế bấm bắt đầu sau khi khách đã lên xe, chuyển trạng thái chuyến sang IN_PROGRESS và ghi nhận thời gian bắt đầu.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (startTrip); TripStatus.java",
        "db": "trip.trips (status = 'IN_PROGRESS', started_at = NOW()); trip.trip_status_history"
    },
    "FR-TRIP-011": {
        "solution": "Hoàn tất chuyến xe (Complete Trip): Tài xế bấm kết thúc cuốc tại điểm trả, chuyển trạng thái chuyến sang COMPLETED, ghi nhận thời gian kết thúc.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (completeTrip); TripCompletedEvent.java",
        "db": "trip.trips (status = 'COMPLETED', completed_at = NOW()); topic Kafka 'trip.completed'"
    },
    "FR-TRIP-012": {
        "solution": "Hủy chuyến xe (Cancel Trip): Xử lý hủy chuyến từ phía Khách hàng hoặc Tài xế với lý do hủy, tính phí hủy (nếu vi phạm điều kiện) và cập nhật CANCELLED.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (cancelTrip); CancelTripRequest.java",
        "db": "trip.trips (status = 'CANCELLED', cancelled_by, cancellation_reason, cancelled_at)"
    },
    "FR-TRIP-013": {
        "solution": "API xem chi tiết thông tin chuyến xe: trả về lộ trình, trạng thái, thông tin tài xế/khách hàng, giá cước, lịch sử thời gian các mốc di chuyển.",
        "code": "backend/trip-service/.../controller/TripController.java (getTripById); TripResponse.java",
        "db": "trip.trips JOIN trip.trip_stops; thông tin tài xế qua DriverClient"
    },
    "FR-TRIP-014": {
        "solution": "API xem danh sách lịch sử chuyến xe có phân trang cho Khách hàng và Tài xế, hỗ trợ lọc theo khoảng thời gian và trạng thái chuyến.",
        "code": "backend/trip-service/.../controller/TripController.java (getCustomerTrips, getDriverTrips); PageResponse.java",
        "db": "trip.trips (Index: idx_trips_customer_id, idx_trips_driver_id, idx_trips_created_at)"
    },
    "FR-TRIP-015": {
        "solution": "Ghi nhận đầy đủ lịch sử thay đổi trạng thái của chuyến (Audit Status History) gồm: trạng thái cũ, trạng thái mới, actor thực hiện, lý do và thời gian.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (recordStatusHistory); TripStatusHistory.java",
        "db": "trip.trip_status_history (trip_id, from_status, to_status, actor_id, actor_role, created_at)"
    },

    # --- MATCHING & DISPATCH REQUIREMENTS (FR-MAT-001 to FR-MAT-015) ---
    "FR-MAT-001": {
        "solution": "Thuật toán tìm kiếm tài xế ứng viên: Quét trong bán kính R = 3km xung quanh điểm đón, lấy danh sách tối đa N tài xế (mặc định 10 tài xế) có độ mới vị trí cao nhất.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (findCandidateDrivers); LocationClient.java",
        "db": "Redis: GEORADIUS driver:locations:geo; lọc status AVAILABLE"
    },
    "FR-MAT-002": {
        "solution": "Xếp hạng tài xế ứng viên (Driver Ranking Algorithm): Sắp xếp theo thứ tự ưu tiên: Khoảng cách gần nhất (Distance) -> Đánh giá sao (Rating) -> Tỷ lệ nhận cuốc (Acceptance Rate).",
        "code": "backend/trip-service/.../util/DriverRanker.java (rankDrivers); TripServiceImpl.java",
        "db": "Tính toán in-memory dựa trên thông tin vị trí và hồ sơ tài xế"
    },
    "FR-MAT-003": {
        "solution": "Chiến lược Dispatch tuần tự (Sequential Dispatch): Gửi offer lần lượt cho từng tài xế có điểm số cao nhất, chờ tối đa 15 giây trước khi chuyển sang tài xế tiếp theo.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (dispatchNextDriverOffer)",
        "db": "trip.driver_offers (trip_id, driver_id, sequence_number INT, expires_at)"
    },
    "FR-MAT-004": {
        "solution": "Chiến lược Dispatch đồng thời (Batch/Broadcast Dispatch): Gửi offer cùng lúc cho nhóm tài xế gần nhất, tài xế nào bấm chấp nhận đầu tiên sẽ thắng cuốc (First-come first-served).",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (broadcastOffers); RedisLockManager.java",
        "db": "Redis Distributed Lock: lock:driver:{driverId} và lock:trip:{tripId}"
    },
    "FR-MAT-005": {
        "solution": "Kiểm soát thời gian phản hồi offer: Mỗi offer gắn hạn phản hồi (expires_at) chính xác 15 giây. Sau 15 giây, client tự động vô hiệu hóa nút bấm và server từ chối request muộn.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (acceptOffer - kiểm tra NOW() <= expires_at)",
        "db": "trip.driver_offers (expires_at TIMESTAMP NOT NULL)"
    },
    "FR-MAT-006": {
        "solution": "Mở rộng bán kính tìm kiếm tự động (Radius Expansion): Nếu sau 30 giây không có tài xế nào nhận cuốc, tự động mở rộng bán kính tìm kiếm từ 3km lên 5km và 8km.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (expandSearchRadius)",
        "db": "trip.trips (current_search_radius_km NUMERIC(4,2)); lặp vòng quét matching"
    },
    "FR-MAT-007": {
        "solution": "Giới hạn thời gian tìm kiếm tối đa (Matching Timeout): Sau tối đa 3 phút tìm kiếm không thành công, hệ thống tự động kết thúc tìm kiếm và báo 'Không tìm thấy tài xế'.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (handleMatchingTimeout); MAX_SEARCH_TIME = 180s",
        "db": "trip.trips (status = 'NO_DRIVER_FOUND', ended_at = NOW())"
    },
    "FR-MAT-008": {
        "solution": "Cơ chế khóa phân tán bảo vệ gán cuốc (Atomic Assignment Lock): Sử dụng Redis Redisson Lock trên key lock:trip:{tripId} khi xử lý request accept để đảm bảo chỉ có 1 tài xế thắng cuốc.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (acceptOffer); RedissonClient",
        "db": "Redis Lock: lock:trip:{tripId} TTL 5s; giải phóng sau khi commit DB"
    },
    "FR-MAT-009": {
        "solution": "Bảo vệ tài xế không nhận cuốc trùng: Sử dụng Redis Lock trên key lock:driver:{driverId} để chặn tài xế bấm nhận 2 cuốc khác nhau cùng một thời điểm.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (acceptOffer); RedissonClient",
        "db": "Redis Lock: lock:driver:{driverId} TTL 5s; kết hợp Partial Unique Index tại PostgreSQL"
    },
    "FR-MAT-010": {
        "solution": "Loại trừ tài xế đã từ chối: Nếu tài xế chủ động REJECT hoặc để EXPIRED offer của một chuyến xe, hệ thống không gửi lại offer của chuyến đó cho tài xế đó trong lượt quét tiếp theo.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (filterEligibleCandidates)",
        "db": "trip.driver_offers (kiểm tra WHERE trip_id = ? AND driver_id NOT IN (danh sách đã từ chối))"
    },
    "FR-MAT-011": {
        "solution": "Thông báo Offer tới thiết bị tài xế: Gửi đẩy thông báo thời gian thực qua WebSocket/Push Notification kèm các thông tin quan trọng: điểm đón, cước ước tính, khoảng cách tới điểm đón.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (notifyDriverOffer); DriverOfferCreatedEvent.java",
        "db": "Kafka Topic: 'driver.offer.created' hoặc WebSocket message STOMP"
    },
    "FR-MAT-012": {
        "solution": "Thông báo hủy Offer cho các tài xế khác: Khi chuyến xe đã có người nhận, ngay lập tức gửi bản tin hủy offer cho tất cả tài xế khác đang nhận offer cùng chuyến đó.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (cancelPendingOffersForTrip)",
        "db": "trip.driver_offers (cập nhật status = 'CANCELLED' cho các offers PENDING còn lại)"
    },
    "FR-MAT-013": {
        "solution": "Hỗ trợ hủy tìm kiếm bởi khách hàng: Khách hàng có thể bấm hủy tìm tài xế khi chuyến đang ở trạng thái SEARCHING mà không bị phạt bất kỳ khoản phí nào.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (cancelTripDuringSearching)",
        "db": "trip.trips (status = 'CANCELLED', cancellation_fee = 0)"
    },
    "FR-MAT-014": {
        "solution": "Theo dõi tỷ lệ chấp nhận cuốc xe của tài xế (Acceptance Rate Metrics): Ghi nhận số lượt nhận/từ chối cuốc để phục vụ thuật toán phân bổ cuốc công bằng.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (recordDriverOfferMetric)",
        "db": "trip.driver_offers (lưu tổng hợp đếm accepted vs rejected theo driver_id)"
    },
    "FR-MAT-015": {
        "solution": "Ghi log chi tiết phiên matching (Matching Audit Session): Lưu vết danh sách tài xế được quét, điểm số, thứ tự gửi offer và nguyên nhân chuyến không tìm được tài xế.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (logMatchingSession)",
        "db": "platform.audit_logs (session_id, trip_id, candidates_count, result, created_at)"
    },

    # --- CANCELLATION REQUIREMENTS (FR-CAN-001 to FR-CAN-007) ---
    "FR-CAN-001": {
        "solution": "API cho Khách hàng hủy chuyến xe: Yêu cầu cung cấp mã lý do hủy (ví dụ: 'WAITING_TOO_LONG', 'CHANGED_MIND', 'DRIVER_ASKED_TO_CANCEL').",
        "code": "backend/trip-service/.../controller/TripController.java (cancelTrip); TripServiceImpl.java; CancelTripRequest.java",
        "db": "trip.trips (status = 'CANCELLED', cancelled_by = 'CUSTOMER', cancellation_reason)"
    },
    "FR-CAN-002": {
        "solution": "API cho Tài xế hủy chuyến xe: Cho phép tài xế hủy chuyến khi có lý do chính đáng ('CANNOT_CONTACT_CUSTOMER', 'VEHICLE_BREAKDOWN', 'CUSTOMER_NO_SHOW').",
        "code": "backend/trip-service/.../controller/DriverTripController.java (cancelTrip); TripServiceImpl.java",
        "db": "trip.trips (status = 'CANCELLED', cancelled_by = 'DRIVER', cancellation_reason)"
    },
    "FR-CAN-003": {
        "solution": "Quy tắc miễn phí hủy chuyến (Grace Period): Khách hàng được miễn phí hủy chuyến nếu bấm hủy trong vòng 3 phút kể từ khi tài xế nhận cuốc (ACCEPTED).",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (calculateCancellationPenalty)",
        "db": "Kiểm tra NOW() - trip.accepted_at <= INTERVAL '3 minutes'; cancellation_fee = 0"
    },
    "FR-CAN-004": {
        "solution": "Áp dụng phí phạt hủy chuyến: Nếu khách hủy sau thời gian miễn phí hoặc sau khi tài xế đã đến điểm đón (ARRIVED), hệ thống tính phí phạt hủy chuyến (ví dụ 10,000 - 20,000 VNĐ).",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java; PricingClient.java",
        "db": "trip.trips (cancellation_fee NUMERIC(12,2)); bồi thường một phần phí phạt vào ví tài xế"
    },
    "FR-CAN-005": {
        "solution": "Xử lý khách không xuất hiện (Customer No-show): Cho phép tài xế báo khách không đến sau khi đã chờ tại điểm đón quá 5 phút và bấm gọi khách ít nhất 2 lần.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (reportCustomerNoShow)",
        "db": "trip.trips (status = 'CANCELLED', cancellation_reason = 'NO_SHOW', cancelled_by = 'DRIVER')"
    },
    "FR-CAN-006": {
        "solution": "Giải phóng tài xế ngay lập tức khi hủy chuyến: Thu hồi trạng thái bận, mở lại trạng thái AVAILABLE cho tài xế để tiếp tục nhận chuyến xe khác.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (releaseDriverAfterCancellation); DriverClient.java",
        "db": "driver.driver_profiles (availability_status = 'AVAILABLE'); xóa key Redis lock"
    },
    "FR-CAN-007": {
        "solution": "Thông báo hủy chuyến hai chiều tức thời: Khi một bên bấm hủy, gửi thông báo đẩy lập tức cho bên còn lại qua WebSocket và SMS để tránh lãng phí thời gian di chuyển.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (broadcastCancellationEvent)",
        "db": "Kafka Topic: 'trip.cancelled'; WebSocket STOMP topic /topic/trip/{tripId}"
    },

    # --- PAYMENT & BILLING REQUIREMENTS (FR-PAY-001 to FR-PAY-013) ---
    "FR-PAY-001": {
        "solution": "Hỗ trợ đa dạng phương thức thanh toán: Tiền mặt (CASH), Ví điện tử (WALLET), Thẻ tín dụng/ghi nợ (CREDIT_CARD) và Cổng thanh toán trực tuyến (VNPay/MoMo/ZaloPay).",
        "code": "backend/payment-service/.../controller/PaymentController.java; PaymentServiceImpl.java; PaymentMethod.java",
        "db": "billing.payments (payment_method VARCHAR(30) NOT NULL)"
    },
    "FR-PAY-002": {
        "solution": "Xử lý thanh toán tiền mặt (CASH): Tài xế trực tiếp thu tiền từ khách khi kết thúc chuyến, bấm xác nhận 'Đã thu tiền' trên ứng dụng tài xế.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (confirmCashPayment); TripCompletedConsumer.java",
        "db": "billing.payments (payment_method = 'CASH', status = 'SUCCESS', collected_by = driver_id)"
    },
    "FR-PAY-003": {
        "solution": "Xử lý thanh toán không tiền mặt: Tự động trừ tiền từ Ví tài khoản của khách hoặc gọi cổng thanh toán thẻ khi nhận được sự kiện TripCompletedEvent.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (processNonCashPayment); TripCompletedConsumer.java",
        "db": "billing.payments (payment_method = 'WALLET'/'CARD', status = 'SUCCESS')"
    },
    "FR-PAY-004": {
        "solution": "Kiểm tra tính Idempotent trong giao dịch thanh toán: Mỗi chuyến chỉ được tạo đúng một bản ghi thanh toán thành công, sử dụng trip_id làm khóa Unique kiểm soát.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (createPayment); PaymentRepository.java",
        "db": "billing.payments (Unique Constraint: uq_payments_trip_id WHERE status = 'SUCCESS')"
    },
    "FR-PAY-005": {
        "solution": "Tích hợp cổng thanh toán bên thứ ba (Payment Gateway): Sinh URL thanh toán an toàn kèm chữ ký số HMAC-SHA256 và tiếp nhận Webhook kết quả giao dịch (IPN).",
        "code": "backend/payment-service/.../controller/PaymentController.java (handlePaymentWebhook); VnpayGatewayService.java",
        "db": "billing.payments (gateway_transaction_id, signature, gateway_response_payload)"
    },
    "FR-PAY-006": {
        "solution": "Xác thực chữ ký số Webhook/IPN: Kiểm tra tính toàn vẹn của gói tin callback từ cổng thanh toán bằng secret key, chống tấn công mạo danh hoặc Replay Attack.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (verifyGatewaySignature); CryptoUtil.java",
        "db": "platform.audit_logs (lưu vết IPN callback payload và kết quả verify signature)"
    },
    "FR-PAY-007": {
        "solution": "Xử lý thanh toán thất bại: Nếu thẻ/ví khách hàng không đủ số dư, chuyển trạng thái thanh toán sang FAILED, ghi nhận nợ chuyến xe và chặn khách đặt chuyến tiếp theo.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (handlePaymentFailure)",
        "db": "billing.payments (status = 'FAILED', failure_reason); iam.users (has_unpaid_trips = true)"
    },
    "FR-PAY-008": {
        "solution": "Quy trình Hoàn tiền (Refund Processing): Hỗ trợ hoàn tiền toàn phần hoặc một phần cho khách khi cuốc xe bị sự cố hoặc khách khiếu nại thành công.",
        "code": "backend/payment-service/.../controller/PaymentController.java (processRefund); PaymentServiceImpl.java; RefundRequest.java",
        "db": "billing.refunds (id, payment_id, trip_id, amount, status = 'COMPLETED', refund_reason)"
    },
    "FR-PAY-009": {
        "solution": "Ràng buộc số tiền hoàn không vượt quá số tiền thanh toán thực tế của chuyến xe (BR-010).",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (processRefund - validate sum(refunds) <= payment.amount)",
        "db": "billing.refunds (amount <= payment.amount); Check Constraint"
    },
    "FR-PAY-010": {
        "solution": "Sinh hóa đơn điện tử (E-Invoice / Receipt): Tạo hóa đơn chi tiết chuyến xe định dạng PDF/JSON gồm: cước chuyến, thuế VAT, phụ phí, tiền tip và mã tra cứu.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (generateReceipt); ReceiptDto.java",
        "db": "billing.payments (receipt_number, invoice_url, created_at)"
    },
    "FR-PAY-011": {
        "solution": "Hỗ trợ khách hàng gửi tiền Tip cho tài xế: Cho phép khách thêm tiền tip (10k, 20k, 50k...) sau khi kết thúc chuyến; 100% tiền tip được cộng vào ví tài xế.",
        "code": "backend/payment-service/.../controller/PaymentController.java (addTip); PaymentServiceImpl.java",
        "db": "billing.payments (tip_amount NUMERIC(12,2)); billing.wallet_entries (entry_type = 'TIP')"
    },
    "FR-PAY-012": {
        "solution": "API xem lịch sử giao dịch thanh toán cho Khách hàng và Quản trị viên đối soát, hỗ trợ lọc theo mã giao dịch, thời gian và phương thức thanh toán.",
        "code": "backend/payment-service/.../controller/PaymentController.java (getPaymentHistory); PageResponse.java",
        "db": "billing.payments (Index: idx_payments_customer_id, idx_payments_trip_id, idx_payments_created_at)"
    },
    "FR-PAY-013": {
        "solution": "Đối soát tự động cuối ngày (Reconciliation Job): Tự động so khớp tổng tiền thanh toán giữa hệ thống và báo cáo đối soát từ Ngân hàng / Cổng thanh toán.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (runDailyReconciliation); ReconciliationScheduler.java",
        "db": "billing.payments; platform.audit_logs (lưu báo cáo sai lệch đối soát nếu có)"
    },

    # --- WALLET & LEDGER REQUIREMENTS (FR-WAL-001 to FR-WAL-010) ---
    "FR-WAL-001": {
        "solution": "Tự động khởi tạo Ví tài khoản cho mỗi tài xế khi hồ sơ được phê duyệt (APPROVED) với số dư ban đầu bằng 0 VNĐ.",
        "code": "backend/payment-service/.../service/impl/WalletServiceImpl.java (createWalletForDriver); DriverCreatedConsumer.java",
        "db": "billing.wallets (id, driver_id, balance = 0.00, currency = 'VND', is_locked = false)"
    },
    "FR-WAL-002": {
        "solution": "Thiết kế sổ cái tài chính bất biến (Immutable Ledger): Cấm sửa trực tiếp số dư trong bảng wallets mà không tạo bản ghi bút toán Wallet Entry đối ứng.",
        "code": "backend/payment-service/.../service/impl/WalletServiceImpl.java (recordEntry); WalletEntryRepository.java",
        "db": "billing.wallet_entries (id, wallet_id, entry_type, amount, balance_after, reference_id, created_at)"
    },
    "FR-WAL-003": {
        "solution": "Cộng tiền cước vào ví tài xế (Credit Entry): Khi hoàn tất chuyến thanh toán không tiền mặt, ghi bút toán CREDIT tăng số dư ví tài xế.",
        "code": "backend/payment-service/.../service/impl/WalletServiceImpl.java (creditDriverWallet); TripCompletedConsumer.java",
        "db": "billing.wallet_entries (entry_type = 'CREDIT', amount = trip_fare, description = 'Trip earnings')"
    },
    "FR-WAL-004": {
        "solution": "Khấu trừ phí hoa hồng nền tảng (Commission Deduction): Khi hoàn tất chuyến (cả tiền mặt và không tiền mặt), ghi bút toán DEBIT trừ tiền hoa hồng công ty (ví dụ 20%).",
        "code": "backend/payment-service/.../service/impl/WalletServiceImpl.java (debitCommission); PaymentServiceImpl.java",
        "db": "billing.wallet_entries (entry_type = 'DEBIT', amount = commission_amount, description = 'Platform commission fee')"
    },
    "FR-WAL-005": {
        "solution": "Tài xế nạp tiền vào ví (Top-up Wallet): Hỗ trợ nạp tiền qua cổng thanh toán / chuyển khoản ngân hàng để duy trì số dư tối thiểu nhận cuốc xe.",
        "code": "backend/payment-service/.../controller/WalletController.java (topUpWallet); WalletServiceImpl.java",
        "db": "billing.wallet_entries (entry_type = 'TOPUP', amount = topup_amount, reference_id = payment_id)"
    },
    "FR-WAL-006": {
        "solution": "Tài xế rút tiền về tài khoản ngân hàng (Withdrawal Request): Tạo yêu cầu rút tiền, xác thực OTP, trừ số dư ví và chuyển trạng thái PENDING chờ giải ngân.",
        "code": "backend/payment-service/.../controller/WalletController.java (withdrawFunds); WalletServiceImpl.java; WithdrawRequest.java",
        "db": "billing.wallet_entries (entry_type = 'WITHDRAWAL', amount = withdraw_amount); billing.withdrawals"
    },
    "FR-WAL-007": {
        "solution": "Kiểm tra số dư tối thiểu khi nhận cuốc: Nếu số dư ví tài xế xuống dưới ngưỡng tối thiểu (ví dụ < 50,000 VNĐ) đối với cuốc tiền mặt, chặn tài xế bật AVAILABLE.",
        "code": "backend/payment-service/.../service/impl/WalletServiceImpl.java (hasSufficientBalance); DriverClient.java",
        "db": "billing.wallets (balance >= MINIMUM_WALLET_THRESHOLD)"
    },
    "FR-WAL-008": {
        "solution": "Số dư ví không bao giờ được âm (Balance Non-negative Constraint): Đảm bảo ràng buộc kiểm tra số dư luôn >= 0 tại tầng cơ sở dữ liệu PostgreSQL.",
        "code": "backend/payment-service/.../service/impl/WalletServiceImpl.java; Wallet.java",
        "db": "billing.wallets (Check Constraint: chk_wallets_balance_positive CHECK (balance >= 0))"
    },
    "FR-WAL-009": {
        "solution": "API xem sao kê biến động số dư ví (Wallet Statement): Cho phép tài xế xem danh sách các giao dịch cộng/trừ tiền theo ngày, tuần, tháng có phân trang.",
        "code": "backend/payment-service/.../controller/WalletController.java (getStatement); WalletServiceImpl.java; PageResponse.java",
        "db": "billing.wallet_entries (Index: idx_wallet_entries_wallet_id, idx_wallet_entries_created_at)"
    },
    "FR-WAL-010": {
        "solution": "Khóa ví tự động khi phát hiện gian lận: Admin có thể khóa ví hoặc hệ thống tự động khóa nếu phát hiện giao dịch bất thường, ngăn chặn rút tiền.",
        "code": "backend/payment-service/.../controller/AdminWalletController.java (lockWallet); WalletServiceImpl.java",
        "db": "billing.wallets (is_locked = true, lock_reason, locked_at)"
    },

    # --- REAL-TIME & WEBSOCKET REQUIREMENTS (FR-RT-001 to FR-RT-08) ---
    "FR-RT-001": {
        "solution": "Thiết lập kết nối WebSocket / WSS bảo mật: Xác thực phiên bằng JWT Token ngay tại bước bắt tay kết nối (Handshake Interceptor).",
        "code": "backend/location-service/.../config/WebSocketConfig.java; JwtHandshakeInterceptor.java",
        "db": "Xác thực token với JWT secret, kiểm tra không bị thu hồi"
    },
    "FR-RT-002": {
        "solution": "Kênh truyền vị trí tài xế thời gian thực: Client tài xế gửi tọa độ GPS định kỳ 3 giây/lần qua bản tin WebSocket STOMP nhẹ định dạng JSON.",
        "code": "backend/location-service/.../controller/LocationWebSocketHandler.java (handleLocationUpdate)",
        "db": "Redis Pub/Sub channel: 'driver:telemetry:stream'"
    },
    "FR-RT-003": {
        "solution": "Truyền trực tiếp vị trí tài xế tới màn hình khách hàng: Server broadcast tọa độ qua kênh riêng của chuyến /topic/trip/{tripId}/location.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (broadcastDriverLocation); SimpMessagingTemplate",
        "db": "Redis Pub/Sub: channel trip:{tripId}:location -> WebSocket broadcast"
    },
    "FR-RT-004": {
        "solution": "Thông báo trạng thái chuyến xe tức thời: Phát sự kiện thay đổi trạng thái (ACCEPTED, ARRIVING, IN_PROGRESS, COMPLETED) qua WebSocket cho cả 2 bên.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (publishTripStatusEvent); SimpMessagingTemplate",
        "db": "WebSocket Topic: /topic/trip/{tripId}/status"
    },
    "FR-RT-005": {
        "solution": "Gửi Offer cuốc xe cho tài xế thời gian thực: Đẩy bản tin offer qua kênh riêng /queue/driver/{driverId}/offers để hiển thị popup nhận cuốc trên App tài xế.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (pushOfferToDriver); SimpMessagingTemplate",
        "db": "WebSocket User Queue: /user/{driverId}/queue/offers"
    },
    "FR-RT-006": {
        "solution": "Cơ chế Heartbeat kiểm tra sống sót (Keep-Alive): Gửi ping-pong định kỳ mỗi 10 giây; tự động ngắt kết nối nếu quá 30 giây không nhận được phản hồi từ thiết bị.",
        "code": "backend/location-service/.../config/WebSocketConfig.java (setHeartbeatValue: 10000ms)",
        "db": "Redis TTL trên session: session:driver:{id} TTL 30s"
    },
    "FR-RT-007": {
        "solution": "Cơ chế tự động kết nối lại (Auto-reconnection): Mobile Client tự động kết nối lại khi mạng chập chờn với thuật toán Exponential Backoff và đồng bộ lại trạng thái chuyến.",
        "code": "backend/trip-service/.../controller/TripController.java (syncCurrentTripState); WebSocketConfig.java",
        "db": "trip.trips (truy vấn chuyến xe active gần nhất của user để khôi phục màn hình)"
    },
    "FR-RT-008": {
        "solution": "Kiểm soát thứ tự bản tin vị trí (Sequence / Timestamp Check): Loại bỏ các bản tin vị trí đến trễ hoặc sai thứ tự (out-of-order) dựa trên deviceTimestamp.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (isLatestTimestamp); LocationDto.java",
        "db": "So sánh device_timestamp với last_timestamp trong Redis driver:location:{id}"
    },

    # --- ADMIN & OPERATIONS REQUIREMENTS (FR-ADM-001 to FR-ADM-007) ---
    "FR-ADM-001": {
        "solution": "Bảng điều khiển quản trị (Admin Dashboard): Cung cấp số liệu thống kê tổng quan thời gian thực: số chuyến xe đang chạy, số tài xế online, doanh thu trong ngày.",
        "code": "backend/trip-service/.../controller/AdminTripController.java; backend/driver-service/.../controller/AdminDriverController.java",
        "db": "Truy vấn aggregate từ trip.trips, driver.driver_profiles, billing.payments"
    },
    "FR-ADM-002": {
        "solution": "Quản lý và xét duyệt hồ sơ tài xế: Giao diện cho Admin tra cứu hồ sơ nộp, xem hình ảnh GPLX, CCCD và thực hiện phê duyệt/từ chối kèm lý do.",
        "code": "backend/driver-service/.../controller/AdminDriverController.java (getPendingProfiles, approveProfile, rejectProfile)",
        "db": "driver.driver_profiles (WHERE status = 'SUBMITTED')"
    },
    "FR-ADM-003": {
        "solution": "Quản lý cấu hình bảng giá và giá động: Cho phép Admin điều chỉnh giá mở cửa, giá mỗi km, tỷ lệ hoa hồng và hệ số giá cao điểm.",
        "code": "backend/pricing-service/.../controller/AdminPricingController.java (updatePricingRule, setSurgeCap)",
        "db": "pricing.pricing_rules (lưu lịch sử cập nhật bảng giá)"
    },
    "FR-ADM-004": {
        "solution": "Giám sát bản đồ chuyến xe thời gian thực (Live Fleet Map): Hiển thị vị trí trực tiếp của tất cả tài xế đang AVAILABLE và các chuyến xe đang IN_PROGRESS trên bản đồ trung tâm.",
        "code": "backend/location-service/.../controller/AdminLocationController.java (getAllActiveDriverLocations)",
        "db": "Redis: GEORADIUS toàn vùng; location.driver_locations"
    },
    "FR-ADM-005": {
        "solution": "Quản lý khiếu nại và can thiệp cuốc xe: Cho phép Operator hủy chuyến cưỡng chế khi có tai nạn/sự cố khẩn cấp và kích hoạt lệnh hoàn tiền cho khách.",
        "code": "backend/trip-service/.../controller/AdminTripController.java (forceCancelTrip); PaymentClient.java",
        "db": "trip.trips (status = 'CANCELLED', cancelled_by = 'ADMIN'); billing.refunds"
    },
    "FR-ADM-006": {
        "solution": "Quản lý tài khoản và khóa người dùng: Cho phép Quản trị viên khóa tạm thời hoặc vĩnh viễn tài khoản khách hàng hoặc tài xế vi phạm quy chế.",
        "code": "backend/iam-service/.../controller/AdminUserController.java (lockUser, unlockUser); UserServiceImpl.java",
        "db": "iam.users (status = 'LOCKED' / 'SUSPENDED', lock_reason)"
    },
    "FR-ADM-007": {
        "solution": "Xuất báo cáo tài chính và đối soát (Export Reports): Cho phép trích xuất báo cáo doanh thu, cước phí, hoa hồng và thuế theo định dạng Excel/CSV.",
        "code": "backend/payment-service/.../controller/AdminPaymentController.java (exportRevenueReport)",
        "db": "billing.payments JOIN billing.wallet_entries"
    },

    # --- EVENT & INTEGRATION REQUIREMENTS (FR-EVT-001 to FR-EVT-008) ---
    "FR-EVT-001": {
        "solution": "Triển khai kiến trúc hướng sự kiện (EDA) qua Apache Kafka: Định nghĩa rõ ràng các Schema sự kiện chuẩn phiên bản 1.0 (Avro/JSON) trong common-library.",
        "code": "backend/common-library/.../event/ (TripCompletedEvent.java, DriverLocationUpdatedEvent.java, DriverOfferCreatedEvent.java)",
        "db": "Apache Kafka Topics: 'trip.completed', 'trip.status.changed', 'driver.offer.created'"
    },
    "FR-EVT-002": {
        "solution": "Áp dụng mẫu hình Transactional Outbox Pattern: Lưu sự kiện vào bảng outbox_events trong cùng Local Database Transaction trước khi CDC/Debezium/Publisher đẩy vào Kafka.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (saveToOutbox); OutboxEventPublisher.java",
        "db": "platform.outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status = 'PENDING')"
    },
    "FR-EVT-003": {
        "solution": "Sự kiện TripCompletedEvent chứa đầy đủ thông tin: tripId, customerId, driverId, fareAmount, paymentMethod, completedAt.",
        "code": "backend/common-library/.../event/TripCompletedEvent.java; TripServiceImpl.java",
        "db": "Kafka Topic: 'trip.completed' (Key = tripId)"
    },
    "FR-EVT-004": {
        "solution": "Consumer xử lý Idempotent: Payment Service kiểm tra xem sự kiện đã từng được xử lý hay chưa trước khi thực hiện ghi nợ/ghi có tài chính.",
        "code": "backend/payment-service/.../event/TripCompletedConsumer.java (consumeTripCompleted); PaymentServiceImpl.java",
        "db": "platform.idempotency_keys (key_value = 'EVENT_TRIP_COMPLETED_' + tripId)"
    },
    "FR-EVT-005": {
        "solution": "Dead Letter Queue (DLQ) cho sự kiện lỗi: Tự động chuyển các bản tin xử lý thất bại sau 3 lần retry sang topic 'trip.completed.DLQ' để đội vận hành kiểm tra.",
        "code": "backend/payment-service/.../config/KafkaConsumerConfig.java (DefaultErrorHandler with DeadLetterPublishingRecoverer)",
        "db": "Kafka Topic: 'trip.completed.DLQ'"
    },
    "FR-EVT-006": {
        "solution": "Sự kiện thay đổi vị trí tài xế (DriverLocationUpdatedEvent): Phát vào Kafka/Redis Stream khi có cập nhật tọa độ phục vụ tính toán mật độ cung cầu của Pricing Service.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (publishLocationEvent)",
        "db": "Kafka Topic / Redis Stream: 'driver.location.updated'"
    },
    "FR-EVT-007": {
        "solution": "Cơ chế Circuit Breaker với Resilience4j: Bảo vệ các cuộc gọi OpenFeign đồng bộ, tự động ngắt mạch khi tỷ lệ lỗi vượt quá 50% và chuyển hướng fallback an toàn.",
        "code": "backend/trip-service/.../client/DriverClient.java; PricingClient.java; application.yml",
        "db": "Resilience4j CircuitBreaker in-memory state (CLOSED -> OPEN -> HALF_OPEN)"
    },
    "FR-EVT-008": {
        "solution": "Distributed Tracing với Micrometer & Zipkin: Tự động truyền Trace ID và Span ID xuyên suốt các HTTP headers và Kafka headers để truy vết luồng xử lý toàn hệ thống.",
        "code": "backend/common-library/.../config/ObservationConfig.java; application.yml (management.tracing)",
        "db": "HTTP Header / Kafka Header: 'traceparent', 'X-B3-TraceId'"
    },

    # --- UI & CLIENT SPECIFICATIONS (UI-CUS-*, UI-DRV-*, UI-ADM-*) ---
    "UI-CUS-001": {
        "solution": "Màn hình Bản đồ trang chủ: Hiển thị vị trí hiện tại của khách hàng trên bản đồ và các xe tài xế khả dụng đang di chuyển xung quanh trong bán kính 2km.",
        "code": "frontend/customer-app; gọi API GET /api/v1/locations/nearby-drivers",
        "db": "Dữ liệu từ Redis GEO qua location-service"
    },
    "UI-CUS-002": {
        "solution": "Màn hình Chọn điểm đến & Báo giá: Hỗ trợ tìm kiếm địa chỉ tự động (Auto-complete), hiển thị các loại xe (Xe máy, 4 chỗ, 7 chỗ) kèm cước phí ước tính tương ứng.",
        "code": "frontend/customer-app; gọi API POST /api/v1/pricing/quote",
        "db": "pricing.fare_quotes"
    },
    "UI-CUS-003": {
        "solution": "Màn hình Trạng thái Tìm xe (Searching Radar): Hiển thị radar hiệu ứng tìm tài xế, đếm ngược thời gian và nút 'Hủy tìm kiếm' cho phép khách hủy khi cần.",
        "code": "frontend/customer-app; gọi API POST /api/v1/trips (bắt đầu) và DELETE /api/v1/trips/{id}/cancel",
        "db": "trip.trips (status = 'SEARCHING')"
    },
    "UI-CUS-004": {
        "solution": "Màn hình Theo dõi Chuyến xe (Live Trip Tracking): Hiển thị thông tin tài xế (tên, avatar, biển số xe, số điện thoại, đánh giá sao) và xe di chuyển theo thời gian thực tới điểm đón.",
        "code": "frontend/customer-app; kết nối WebSocket /topic/trip/{tripId}/location",
        "db": "trip.trips; driver.driver_profiles; location.driver_locations"
    },
    "UI-CUS-005": {
        "solution": "Màn hình Trong chuyến đi (In-Trip Screen): Hiển thị tuyến đường di chuyển, thời gian dự kiến tới nơi (ETA) và nút gọi điện khẩn cấp SOS.",
        "code": "frontend/customer-app; cập nhật liên tục tọa độ từ WebSocket",
        "db": "trip.trips (status = 'IN_PROGRESS')"
    },
    "UI-CUS-006": {
        "solution": "Màn hình Thanh toán & Đánh giá chuyến xe: Hiển thị hóa đơn tổng cước, cho phép chọn tip tiền cho tài xế và chấm điểm đánh giá (1-5 sao) kèm nhận xét.",
        "code": "frontend/customer-app; gọi API POST /api/v1/payments/trips/{id}/tip và POST /api/v1/trips/{id}/rate",
        "db": "billing.payments; trip.trips (rating, review_comment)"
    },
    "UI-CUS-007": {
        "solution": "Màn hình Lịch sử chuyến xe: Danh sách các chuyến đã đi kèm chi tiết điểm đón/trả, thời gian, số tiền và trạng thái (Hoàn thành / Đã hủy).",
        "code": "frontend/customer-app; gọi API GET /api/v1/trips/my-trips",
        "db": "trip.trips (phân trang PageResponse)"
    },
    "UI-CUS-008": {
        "solution": "Màn hình Quản lý Hồ sơ cá nhân: Cập nhật tên, ảnh đại diện, email và đổi mật khẩu.",
        "code": "frontend/customer-app; gọi API PUT /api/v1/users/profile",
        "db": "iam.users"
    },
    "UI-CUS-009": {
        "solution": "Màn hình Trung tâm Hỗ trợ & Khiếu nại: Gửi yêu cầu trợ giúp về chuyến xe bị tính sai giá hoặc quên đồ trên xe.",
        "code": "frontend/customer-app; gọi API POST /api/v1/support/tickets",
        "db": "platform.audit_logs"
    },

    "UI-DRV-001": {
        "solution": "Màn hình Trang chủ Tài xế với nút gạt Online/Offline: Chuyển đổi trạng thái sẵn sàng nhận cuốc, hiển thị bản đồ định vị vị trí hiện tại của xe.",
        "code": "frontend/driver-app; gọi API PUT /api/v1/drivers/availability",
        "db": "driver.driver_profiles (availability_status)"
    },
    "UI-DRV-002": {
        "solution": "Popup Nhận Cuốc Xe (Driver Offer Dialog): Hiển thị toàn màn hình kèm âm thanh thông báo: điểm đón, khoảng cách đón (km), điểm trả, cước ước tính và đồng hồ đếm ngược 15s.",
        "code": "frontend/driver-app; lắng nghe WebSocket /user/queue/offers; gọi API POST /api/v1/trips/offers/{id}/accept",
        "db": "trip.driver_offers (expires_at)"
    },
    "UI-DRV-003": {
        "solution": "Màn hình Chỉ đường đón khách (Navigation to Pickup): Tích hợp bản đồ dẫn đường chỉ dẫn tuyến đường ngắn nhất tới điểm đón của khách.",
        "code": "frontend/driver-app; tích hợp Map SDK (Mapbox/Google Maps); gọi API PUT /api/v1/trips/{id}/status (ARRIVING)",
        "db": "trip.trips (status = 'ARRIVING')"
    },
    "UI-DRV-004": {
        "solution": "Màn hình Đã đến điểm đón: Hiển thị thông tin khách hàng, nút gọi điện thoại liên hệ trực tiếp và nút trượt 'Bắt đầu chuyến đi' khi khách đã lên xe.",
        "code": "frontend/driver-app; gọi API PUT /api/v1/trips/{id}/status (IN_PROGRESS)",
        "db": "trip.trips (status = 'IN_PROGRESS', started_at = NOW())"
    },
    "UI-DRV-005": {
        "solution": "Màn hình Trong chuyến đi & Kết thúc cuốc: Bản đồ dẫn đường tới điểm trả khách và nút trượt xác nhận 'Hoàn thành chuyến xe' khi tới nơi.",
        "code": "frontend/driver-app; gọi API PUT /api/v1/trips/{id}/complete",
        "db": "trip.trips (status = 'COMPLETED', completed_at = NOW())"
    },
    "UI-DRV-006": {
        "solution": "Màn hình Thu tiền (Cash Collection Dialog): Nếu chuyến tiền mặt, hiển thị số tiền chính xác cần thu từ khách và nút xác nhận 'Đã thu đủ tiền mặt'.",
        "code": "frontend/driver-app; gọi API POST /api/v1/payments/confirm-cash",
        "db": "billing.payments (payment_method = 'CASH', status = 'SUCCESS')"
    },
    "UI-DRV-007": {
        "solution": "Màn hình Ví Tài Xế & Thu Nhập: Xem số dư khả dụng, thu nhập hôm nay, chi tiết các chuyến chạy trong ngày và nút 'Rút tiền' về tài khoản ngân hàng.",
        "code": "frontend/driver-app; gọi API GET /api/v1/wallets/my-wallet; GET /api/v1/wallets/statement",
        "db": "billing.wallets; billing.wallet_entries"
    },
    "UI-DRV-008": {
        "solution": "Màn hình Đăng ký & Nộp hồ sơ tài xế: Nhập thông tin xe, chụp ảnh bằng lái xe (GPLX), CCCD và theo dõi tiến độ duyệt hồ sơ.",
        "code": "frontend/driver-app; gọi API POST /api/v1/drivers/profile; POST /api/v1/drivers/vehicles",
        "db": "driver.driver_profiles; driver.vehicles; driver.driver_documents"
    },

    "UI-ADM-001": {
        "solution": "Trang Tổng quan Dashboard: Biểu đồ trực quan hóa doanh thu, biểu đồ nhiệt nhu cầu đặt xe theo khu vực (Heatmap) và các chỉ số vận hành thời gian thực.",
        "code": "frontend/admin-portal; gọi API GET /api/v1/admin/dashboard/stats",
        "db": "Truy vấn thống kê tổng hợp từ PostgreSQL"
    },
    "UI-ADM-002": {
        "solution": "Trang Quản lý Hồ sơ Tài xế: Danh sách tài xế chờ duyệt, giao diện xem ảnh giấy tờ, nút 'Phê duyệt' và nút 'Từ chối' kèm lý do.",
        "code": "frontend/admin-portal; gọi API GET /api/v1/admin/drivers/pending; POST /api/v1/admin/drivers/{id}/approve",
        "db": "driver.driver_profiles (status = 'SUBMITTED')"
    },
    "UI-ADM-003": {
        "solution": "Trang Cấu hình Bảng giá: Giao diện biểu mẫu thiết lập giá cơ sở, giá km, giá thời gian và phụ phí theo từng thành phố/loại xe.",
        "code": "frontend/admin-portal; gọi API PUT /api/v1/admin/pricing/rules/{id}",
        "db": "pricing.pricing_rules"
    },
    "UI-ADM-004": {
        "solution": "Trang Bản đồ Giám sát Vận hành (Live Fleet Monitor): Bản đồ toàn màn hình hiển thị toàn bộ tài xế trực tuyến và các chuyến xe đang diễn ra.",
        "code": "frontend/admin-portal; gọi API GET /api/v1/admin/locations/active-drivers",
        "db": "location.driver_locations"
    },
    "UI-ADM-005": {
        "solution": "Trang Đối soát Tài chính & Báo cáo: Xem danh sách giao dịch nạp/rút ví, lịch sử thanh toán chuyến xe và nút kết xuất file báo cáo Excel.",
        "code": "frontend/admin-portal; gọi API GET /api/v1/admin/payments/reports",
        "db": "billing.payments; billing.wallet_entries"
    },

    # --- DATA & VALIDATION REQUIREMENTS (DR-GEO-*, DR-VAL-*, DR-PRV-*) ---
    "DR-GEO-001": {
        "solution": "Tất cả dữ liệu tọa độ địa lý trong hệ thống bắt buộc sử dụng Hệ quy chiếu Không gian Quốc tế WGS84 (EPSG:4326).",
        "code": "backend/location-service/.../entity/DriverLocation.java; LocationRepository.java",
        "db": "PostgreSQL PostGIS: GEOMETRY(Point, 4326)"
    },
    "DR-GEO-002": {
        "solution": "Các trường tọa độ lưu trữ dưới dạng vĩ độ (latitude) và kinh độ (longitude) kiểu số thực NUMERIC(10, 7) đảm bảo độ chính xác tới centimet.",
        "code": "backend/common-library/.../dto/LocationDto.java (BigDecimal latitude, BigDecimal longitude)",
        "db": "Cột latitude NUMERIC(10,7), longitude NUMERIC(10,7) trong tất cả các bảng có định vị"
    },
    "DR-GEO-003": {
        "solution": "Chỉ mục không gian GiST (Generalized Search Tree) được tạo trên tất cả các cột kiểu GEOMETRY để tối ưu hóa truy vấn vùng và bán kính ST_DWithin.",
        "code": "Database Migration Script: V1__init_schemas.sql",
        "db": "CREATE INDEX idx_driver_locations_geom ON location.driver_locations USING GIST (geom);"
    },
    "DR-GEO-004": {
        "solution": "Tích hợp H3 Spatial Index (Hexagonal Hierarchical Spatial Index) độ phân giải Resolution 7-8 để phân vùng tính toán mật độ cung cầu và giá động.",
        "code": "backend/pricing-service/.../util/H3GeoUtil.java; PricingServiceImpl.java",
        "db": "pricing.fare_quotes (h3_index VARCHAR(20))"
    },
    "DR-GEO-005": {
        "solution": "Lưu trữ hình học hành trình di chuyển của chuyến xe dưới dạng PostGIS LineString để vẽ lại tuyến đường đã đi trên bản đồ.",
        "code": "backend/trip-service/.../entity/Trip.java; TripRepository.java",
        "db": "trip.trips (actual_route_geom GEOMETRY(LineString, 4326))"
    },
    "DR-GEO-006": {
        "solution": "Tự động phát hiện và loại bỏ các tọa độ GPS nhảy cóc bất thường (GPS Jumps/Teleportation) nếu vận tốc tính toán vượt quá 150 km/h.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (isRealisticMovement)",
        "db": "location.driver_locations (bỏ qua bản tin telemetry nếu delta_distance / delta_time > 150km/h)"
    },

    "DR-VAL-001": {
        "solution": "Kiểm tra toàn diện dữ liệu đầu vào (Input Validation) tại API Boundary bằng Spring Validation (@Valid, @NotNull, @Size, @Pattern).",
        "code": "backend/common-library/.../dto/ (tất cả các request DTO đều có Jakarta Validation annotations)",
        "db": "Check constraints và Not Null constraints tại tầng database schema"
    },
    "DR-VAL-002": {
        "solution": "Địa chỉ tìm kiếm geocoding bắt buộc lưu trữ cả chuỗi địa chỉ hiển thị đã chuẩn hóa và cặp tọa độ (lat, lng) tương ứng.",
        "code": "backend/common-library/.../dto/LocationDto.java (displayAddress, latitude, longitude)",
        "db": "trip.trips (pickup_address, pickup_lat, pickup_lng, dropoff_address, dropoff_lat, dropoff_lng)"
    },
    "DR-VAL-003": {
        "solution": "Chuẩn hóa định dạng trước khi kiểm tra tính duy nhất: Biển số xe bỏ dấu gạch/chấm (VD: '51F12345'), số điện thoại chuẩn hóa E.164 (+84901234567), email viết thường toàn bộ.",
        "code": "backend/iam-service/.../service/impl/AuthServiceImpl.java; backend/driver-service/.../service/impl/VehicleServiceImpl.java",
        "db": "iam.users (email = LOWER(TRIM(email))); driver.vehicles (license_plate = UPPER(REGEXP_REPLACE(plate, '[^A-Z0-9]', '')))"
    },
    "DR-VAL-004": {
        "solution": "Kiểm tra tệp tải lên (File Upload Validation): Kiểm tra Content-Type thực qua Magic Bytes (chỉ chấp nhận JPEG, PNG, PDF), dung lượng tối đa 5MB, ngăn chặn tệp độc hại.",
        "code": "backend/driver-service/.../service/impl/DriverDocumentServiceImpl.java (validateUploadedFile); FileUploadUtil.java",
        "db": "driver.driver_documents (file_path, file_size_bytes, mime_type)"
    },
    "DR-VAL-005": {
        "solution": "Chống tấn công gán trường hàng loạt (Mass Assignment Protection): Sử dụng các Request DTO chuyên biệt, cấm nhận Entity trực tiếp tại Controller.",
        "code": "backend/ (tất cả các service sử dụng Request DTOs: CreateTripRequest, UpdateProfileRequest, v.v.)",
        "db": "Các trường nhạy cảm như role, is_active, balance không bao giờ có trong Request DTO thông thường"
    },
    "DR-VAL-006": {
        "solution": "Kiểm tra schema và tính hợp lệ của dữ liệu phản hồi từ dịch vụ bên thứ ba (Bản đồ OpenStreetMap/Google Maps, Cổng thanh toán) trước khi xử lý.",
        "code": "backend/pricing-service/.../client/MapServiceClient.java; backend/payment-service/.../client/PaymentGatewayClient.java",
        "db": "platform.audit_logs (lưu vết lỗi tích hợp nếu bên thứ ba trả về payload sai định dạng)"
    },

    "DR-PRV-001": {
        "solution": "Thu thập vị trí theo nguyên tắc tối thiểu cần thiết: Chỉ bật thu thập GPS khi tài xế đang ở trạng thái AVAILABLE hoặc đang thực hiện chuyến đi.",
        "code": "frontend/driver-app (dừng Background Location Service khi tài xế OFFLINE); LocationServiceImpl.java",
        "db": "location.driver_locations (chỉ cập nhật khi availability_status != 'OFFLINE')"
    },
    "DR-PRV-002": {
        "solution": "Vị trí chính xác chỉ được chia sẻ trong thời gian chuyến xe diễn ra (ACCEPTED -> COMPLETED). Ngay khi cuốc kết thúc, kênh chia sẻ tọa độ lập tức bị hủy bỏ.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java; WebSocketConfig.java",
        "db": "Redis Pub/Sub channel đóng lại khi chuyến COMPLETED"
    },
    "DR-PRV-003": {
        "solution": "Phân loại dữ liệu nhạy cảm cao (PII): Số CCCD, ảnh bằng lái xe, lịch sử vị trí chi tiết và thông tin ví được phân quyền truy cập nghiêm ngặt.",
        "code": "backend/driver-service/.../controller/DriverController.java (chỉ chủ sở hữu hoặc ADMIN được xem hồ sơ chi tiết)",
        "db": "driver.driver_documents, driver.driver_profiles; mã hóa trường nhạy cảm nếu cần thiết"
    },
    "DR-PRV-004": {
        "solution": "Che giấu thông tin cá nhân (PII Masking): Số điện thoại hiển thị trên giao diện đối phương bị che một phần (ví dụ: '090****567'), che email.",
        "code": "backend/common-library/.../util/DataMaskingUtil.java (maskPhoneNumber, maskEmail); UserProfileResponse.java",
        "db": "Lưu đầy đủ trong DB nhưng mask khi serialize ra DTO cho các actor không phải ADMIN"
    },
    "DR-PRV-005": {
        "solution": "Chính sách lưu giữ dữ liệu: Tự động xóa hoặc di chuyển vào kho lưu trữ (Archive) dữ liệu vị trí chi tiết sau 90 ngày; giữ lại thông tin giao dịch tài chính 5 năm theo luật kế toán.",
        "code": "backend/location-service/.../job/LocationDataRetentionJob.java; ScheduledArchiver.java",
        "db": "location.driver_telemetry_history (DELETE WHERE recorded_at < NOW() - INTERVAL '90 days')"
    },
    "DR-PRV-006": {
        "solution": "Hỗ trợ quyền ẩn danh / xóa tài khoản (GDPR / Right to be Forgotten): Ẩn danh hóa thông tin cá nhân (tên, SĐT, email thành chuỗi hash) trong khi vẫn bảo toàn dữ liệu giao dịch tài chính đối soát.",
        "code": "backend/iam-service/.../service/impl/UserServiceImpl.java (anonymizeUserAccount)",
        "db": "iam.users (full_name = 'ANONYMIZED_USER', email = 'deleted_user_' + id + '@deleted.local', status = 'DELETED')"
    },

    # --- NON-FUNCTIONAL REQUIREMENTS (NFR-PERF-*, NFR-REL-*, NFR-SEC-*, NFR-MNT-*, NFR-USA-*, NFR-PRV-*, NFR-COMP-*) ---
    "NFR-PERF-001": {
        "solution": "Độ trễ cập nhật vị trí thời gian thực: 95% các bản tin GPS từ khi tài xế gửi tới khi khách hàng nhận được qua WebSocket hoàn thành trong thời gian <= 1.0 giây.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java; Redis Pub/Sub; In-memory event routing",
        "db": "Redis Pub/Sub (latency < 10ms); WebSocket push trực tiếp"
    },
    "NFR-PERF-002": {
        "solution": "Khả năng chịu tải Telemetry: Location Service xử lý ổn định luồng dữ liệu của ít nhất 100 tài xế đồng thời gửi telemetry chu kỳ 3 giây (33 updates/giây) với CPU < 30%.",
        "code": "backend/location-service/.../controller/LocationController.java; Netty WebSocket Worker Pool",
        "db": "Redis GEOADD O(log(N)) hiệu năng cực cao, đáp ứng hàng ngàn TPS"
    },
    "NFR-PERF-003": {
        "solution": "Độ trễ truy vấn tài xế gần: 95% yêu cầu quét tìm tài xế gần điểm đón hoàn thành <= 200ms bằng cách sử dụng bộ đệm Redis GEO thay vì quét trực tiếp DB.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (getNearbyDrivers)",
        "db": "Redis GEORADIUS/GEOSEARCH (phản hồi trong 5-15ms)"
    },
    "NFR-PERF-004": {
        "solution": "Độ trễ API nghiệp vụ: 95% API đọc thông thường phản hồi <= 500ms; 95% API ghi thông thường phản hồi <= 1.000ms.",
        "code": "Tất cả các Service áp dụng Connection Pooling (HikariCP), Virtual Threads (Java 21) và chỉ mục Index tối ưu",
        "db": "PostgreSQL: B-Tree Index trên các khóa ngoại và điều kiện lọc thường dùng"
    },
    "NFR-PERF-005": {
        "solution": "Độ trễ sinh báo giá (Fare Quote): 95% yêu cầu tính giá cước hoàn thành <= 2.0 giây trong điều kiện nhà cung cấp bản đồ phản hồi đúng SLA.",
        "code": "backend/pricing-service/.../service/impl/PricingServiceImpl.java (calculateFare); Async Map Client",
        "db": "pricing.pricing_rules (cached trong bộ nhớ Caffeine Cache cục bộ để không truy vấn DB nhiều lần)"
    },
    "NFR-PERF-006": {
        "solution": "Quy trình ghép xe (Matching) có thời hạn tối đa: Trả kết quả thành công hoặc 'Không tìm thấy tài xế' trong thời gian tối đa 3 phút (cấu hình được).",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (MAX_SEARCH_TIMEOUT = 180s)",
        "db": "trip.trips (status = 'NO_DRIVER_FOUND' nếu quá timeout)"
    },
    "NFR-PERF-007": {
        "solution": "Kiểm soát tương tranh cao: Dưới tải 100 yêu cầu đồng thời nhận cùng một cuốc xe hoặc tranh chấp một tài xế, duy nhất 1 yêu cầu thành công, các yêu cầu còn lại nhận thông báo lỗi thích hợp.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (acceptOffer); Redis Redisson Lock",
        "db": "PostgreSQL: Partial Unique Index idx_trips_driver_active_unique bảo vệ tầng cuối"
    },
    "NFR-PERF-008": {
        "solution": "Tất cả các API trả về danh sách (chuyến đi, lịch sử giao dịch, hồ sơ) bắt buộc phải phân trang (Pagination) với Pageable và PageResponse, cấm truy vấn không giới hạn.",
        "code": "backend/common-library/.../dto/PageResponse.java; Spring Data Pageable (mặc định pageSize = 10 hoặc 20, max = 100)",
        "db": "PostgreSQL: LIMIT ? OFFSET ?"
    },

    "NFR-REL-001": {
        "solution": "Khôi phục trạng thái chuyến xe khi mất kết nối mạng: Client khi reconnect tự động gọi API đồng bộ lấy lại trạng thái chuyến xe mới nhất từ Server, không bị mất dữ liệu.",
        "code": "backend/trip-service/.../controller/TripController.java (getCurrentActiveTrip); TripServiceImpl.java",
        "db": "trip.trips (trạng thái lưu bền vững trên PostgreSQL)"
    },
    "NFR-REL-002": {
        "solution": "Loại bỏ bản tin telemetry trùng lặp hoặc đến sai thứ tự: So khớp timestamp, chỉ cập nhật vị trí mới nếu timestamp lớn hơn timestamp hiện tại trong cache.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (validateSequenceTimestamp)",
        "db": "Redis key driver:location:{id} lưu timestamp bản tin cuối cùng"
    },
    "NFR-REL-003": {
        "solution": "Không bao giờ gán 1 tài xế cho 2 chuyến xe hoạt động đồng thời (Zero Duplicate Assignment) nhờ cơ chế khóa phân tán Redis kết hợp Partial Unique Index tại DB.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java; TripRepository.java",
        "db": "trip.trips (Partial Unique Index: idx_trips_driver_active_unique)"
    },
    "NFR-REL-004": {
        "solution": "Đảm bảo không bị mất sự kiện TripCompleted và các khoản tiền tài chính: Sử dụng Transactional Outbox Pattern kết hợp Kafka Acknowledgment (acks=all).",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java; backend/payment-service/.../event/TripCompletedConsumer.java",
        "db": "platform.outbox_events; Kafka Replication Factor = 3 (production)"
    },
    "NFR-REL-005": {
        "solution": "Giao tiếp với dịch vụ bên ngoài (Bản đồ, Cổng thanh toán) có cấu hình Timeout (3s), Circuit Breaker và Retry tối đa 3 lần cho các thao tác Idempotent.",
        "code": "backend/pricing-service/.../client/MapServiceClient.java; backend/payment-service/.../client/PaymentGatewayClient.java",
        "db": "Resilience4j Retry & TimeLimiter config"
    },
    "NFR-REL-006": {
        "solution": "Sự cố dịch vụ Định giá (Pricing Service) sau khi báo giá đã được snapshot vào chuyến xe không làm ảnh hưởng đến giá cước chuyến đã chốt.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (sử dụng fare_amount đã snapshot trong trip.trips)",
        "db": "trip.trips (lưu cứng fare_amount, không gọi lại pricing-service khi hoàn thành chuyến)"
    },
    "NFR-REL-007": {
        "solution": "Nếu Payment/Wallet Service gặp sự cố tạm thời, chuyến xe vẫn ghi nhận hoàn thành (COMPLETED); sự kiện được giữ trong Kafka để consumer xử lý lại khi service phục hồi.",
        "code": "backend/payment-service/.../event/TripCompletedConsumer.java; Kafka Consumer Retry",
        "db": "Kafka Broker lưu trữ message; Consumer tự động offset commit khi xử lý thành công"
    },
    "NFR-REL-008": {
        "solution": "Các tác vụ nền (Scheduled Jobs) và Kafka Consumers tiếp tục vận hành bình thường sau khi khởi động lại mà không sinh ra tác dụng phụ trùng lặp.",
        "code": "backend/payment-service/.../event/TripCompletedConsumer.java; IdempotencyService.java",
        "db": "platform.idempotency_keys kiểm soát chống chạy lặp"
    },

    "NFR-SEC-001": {
        "solution": "Toàn bộ giao tiếp mạng công khai bắt buộc sử dụng giao thức mã hóa HTTPS và WSS với phiên bản TLS 1.2 hoặc TLS 1.3.",
        "code": "backend/api-gateway/.../config/SecurityConfig.java; Nginx / Reverse Proxy SSL Termination",
        "db": "Kết nối PostgreSQL sử dụng sslmode=prefer hoặc verify-full"
    },
    "NFR-SEC-002": {
        "solution": "Mật khẩu người dùng được băm an toàn bằng thuật toán BCrypt với độ phức tạp Work Factor = 12, có Salt ngẫu nhiên chống tấn công Rainbow Table.",
        "code": "backend/iam-service/.../config/SecurityConfig.java (BCryptPasswordEncoder)",
        "db": "iam.users (cột password_hash dài 60 ký tự BCrypt)"
    },
    "NFR-SEC-003": {
        "solution": "Các khóa bí mật (JWT Secret, Database Credentials, Payment Keys) được cấu hình qua Biến Môi Trường (Environment Variables) hoặc Config Server, không hard-code trong Git.",
        "code": "backend/common-library/.../security/JwtUtil.java (@Value(\"${jwt.secret}\")); application.yml",
        "db": "Quản lý tập trung qua config-service và file môi trường .env"
    },
    "NFR-SEC-004": {
        "solution": "Áp dụng nguyên tắc đặc quyền tối thiểu (Least Privilege): Kiểm tra quyền chi tiết ở mức Object-level (chỉ sửa/xem dữ liệu của chính mình) và Function-level (@PreAuthorize).",
        "code": "backend/common-library/.../security/SecurityUtils.java; @PreAuthorize(\"hasRole('ADMIN') or #userId == principal.id\")",
        "db": "iam.roles; iam.user_roles"
    },
    "NFR-SEC-005": {
        "solution": "Token WebSocket được xác thực trong bước Handshake ban đầu, có thời hạn ngắn và được liên kết chặt chẽ với userId của phiên kết nối.",
        "code": "backend/location-service/.../config/JwtHandshakeInterceptor.java",
        "db": "Chỉ lưu session mapping user_id -> WebSocketSessionId trong bộ nhớ"
    },
    "NFR-SEC-006": {
        "solution": "Hệ thống áp dụng các biện pháp phòng vệ an ninh mạng: Chống SQL Injection (dùng Hibernate Parameterized Queries), chống XSS (sanitize input), chống CSRF (Stateless JWT).",
        "code": "backend/api-gateway/.../filter/JwtAuthenticationGatewayFilter.java; backend/iam-service/.../config/SecurityConfig.java",
        "db": "Spring Data JPA tự động tham số hóa mọi câu lệnh SQL"
    },
    "NFR-SEC-007": {
        "solution": "Áp dụng giới hạn tần suất yêu cầu (Rate Limiting) tại API Gateway sử dụng Redis Token Bucket: 10 requests/s đối với login, 20 requests/s đối với telemetry.",
        "code": "backend/api-gateway/.../config/GatewayConfig.java (RedisRateLimiter); KeyResolver",
        "db": "Redis Token Bucket: request_rate_limiter.{ip}"
    },
    "NFR-SEC-008": {
        "solution": "Dữ liệu nhạy cảm (thông tin định danh, tài khoản ngân hàng, tọa độ) được mã hóa khi truyền qua mạng (HTTPS/TLS) và kiểm soát truy cập nghiêm ngặt tại DB.",
        "code": "backend/driver-service/.../entity/DriverProfile.java; backend/payment-service/.../entity/Wallet.java",
        "db": "PostgreSQL Data-at-Rest Encryption (pgcrypto / TDE)"
    },
    "NFR-SEC-009": {
        "solution": "Log hệ thống tuyệt đối không chứa thông tin nhạy cảm: lọc bỏ password, JWT token, thẻ tín dụng, OTP và PII cá nhân trong log files.",
        "code": "backend/common-library/.../util/LogSanitizer.java; logback-spring.xml (MaskingPatternLayout)",
        "db": "Log xuất qua SLF4J / Logback không ghi log raw request body chứa mật khẩu"
    },
    "NFR-SEC-010": {
        "solution": "Callback Webhook thanh toán bắt buộc xác minh chữ ký số (Checksum HMAC-SHA256) và đối chiếu chính xác mã đơn hàng (tripId), số tiền (amount) và đơn vị tiền tệ.",
        "code": "backend/payment-service/.../service/impl/PaymentServiceImpl.java (verifyGatewayCallback)",
        "db": "billing.payments (so khớp amount với số tiền callback)"
    },
    "NFR-SEC-011": {
        "solution": "Tệp tin tải lên (Upload) được lưu trữ tại phân vùng phi thực thi (Object Storage / Static Directory riêng), cấm tuyệt đối quyền thực thi mã lệnh.",
        "code": "backend/driver-service/.../service/impl/DriverDocumentServiceImpl.java; FileStorageService.java",
        "db": "Chỉ lưu đường dẫn URL tệp trong DB; tệp nằm trên thư mục cách ly hoặc AWS S3/MinIO"
    },
    "NFR-SEC-012": {
        "solution": "Phía Client tuyệt đối không được tự quyết định giá cước, trạng thái chuyến, gán tài xế hay kết quả thanh toán. Toàn bộ nghiệp vụ đều được xác thực và xử lý tập trung tại Server.",
        "code": "backend/ (tất cả các logic tính giá, chuyển trạng thái, gán tài xế đều nằm trong Service layer)",
        "db": "Toàn vẹn dữ liệu được đảm bảo bởi các Database Triggers và Ràng buộc nghiệp vụ"
    },
    "NFR-SEC-013": {
        "solution": "Thường xuyên quét lỗ hổng thư viện phụ thuộc (Dependency Vulnerability Scanning) bằng OWASP Dependency-Check hoặc Snyk trước khi phát hành phiên bản.",
        "code": "build.gradle (kiểm tra các thư viện Spring Boot 3.4.4, Jackson, Netty không có CVE trọng yếu)",
        "db": "CI/CD Pipeline Security Gate"
    },

    "NFR-USA-001": {
        "solution": "Giao diện người dùng di động được tối ưu hóa cho thao tác tối thiểu: nút bấm to, rõ ràng, dễ chạm bằng một tay khi đang di chuyển.",
        "code": "frontend/ (thiết kế UI Mobile Flutter/React Native)",
        "db": "Quy chuẩn thiết kế UX Mobile"
    },
    "NFR-USA-002": {
        "solution": "Hiển thị trạng thái rõ ràng, minh bạch: Trạng thái MATCHING, offer hết hạn, mất kết nối mạng, tín hiệu GPS yếu đều có chỉ báo trực quan.",
        "code": "frontend/ (NetworkStatusIndicator, GpsStatusIndicator, SearchingDialog)",
        "db": "Phản hồi qua mã lỗi chuẩn ErrorResponse"
    },
    "NFR-USA-003": {
        "solution": "Nút bấm hành động (Đặt xe, Nhận chuyến, Thanh toán) có cơ chế Debounce / Disable ngay sau khi bấm để ngăn người dùng bấm nhiều lần gây trùng lặp.",
        "code": "frontend/ (UI Debounce); backend/ (kèm Header Idempotency-Key phòng thủ)",
        "db": "platform.idempotency_keys"
    },
    "NFR-USA-004": {
        "solution": "Định dạng hiển thị nhất quán: Tiền tệ định dạng theo chuẩn Việt Nam Đồng ('50.000 đ'), khoảng cách hiển thị km ('3,5 km'), thời gian hiển thị phút ('12 phút').",
        "code": "frontend/ (NumberFormat, DistanceFormat utilities); backend/ (LocationDto, FareBreakdownDto)",
        "db": "Đơn vị lưu trữ chuẩn: VNĐ, Mét/Km, Giây/Phút"
    },
    "NFR-USA-005": {
        "solution": "Thông báo lỗi thân thiện với người dùng (User-friendly Error Messages): Không hiển thị Stack Trace kỹ thuật hoặc mã lỗi database ra màn hình client.",
        "code": "backend/common-library/.../exception/GlobalExceptionHandler.java; ErrorResponse.java",
        "db": "Trả về mã lỗi chuẩn RFC 7807 ProblemDetail: code, message, correlationId"
    },
    "NFR-USA-006": {
        "solution": "Hỗ trợ khả năng tiếp cận (Accessibility - a11y): Tương thích trình đọc màn hình, độ tương phản màu sắc đạt chuẩn WCAG 2.1 AA.",
        "code": "frontend/ (WCAG Compliance guidelines)",
        "db": "Tiêu chuẩn thiết kế giao diện"
    },

    "NFR-MNT-001": {
        "solution": "Tất cả các REST API đều được tài liệu hóa tự động bằng OpenAPI 3.0 / Swagger UI (springdoc-openapi); các Event đều có Schema định nghĩa trong common-library.",
        "code": "backend/ (mỗi service tích hợp springdoc-openapi-starter-webmvc-ui, truy cập qua /swagger-ui.html)",
        "db": "OpenAPI Documentation"
    },
    "NFR-MNT-002": {
        "solution": "Quản lý phiên bản API (API Versioning): Sử dụng tiền tố URI '/api/v1/...' cho tất cả endpoints; thay đổi không tương thích bắt buộc chuyển sang '/api/v2/...'.",
        "code": "backend/ (tất cả RequestMapping có tiền tố '/api/v1/...')",
        "db": "API Versioning Strategy"
    },
    "NFR-MNT-003": {
        "solution": "Bao phủ kiểm thử tự động (Automated Unit & Integration Tests): Đầy đủ Unit Tests cho logic State Machine, Matching, Pricing, Payment và Wallet bằng JUnit 5 & Mockito.",
        "code": "backend/trip-service/.../TripServiceImplTest.java; backend/pricing-service/.../PricingServiceImplTest.java; backend/payment-service/.../PaymentServiceImplTest.java",
        "db": "H2 In-memory Database / Testcontainers cho kiểm thử tích hợp"
    },
    "NFR-MNT-004": {
        "solution": "Ranh giới Bounded Context rõ ràng: Mỗi microservice sở hữu mã nguồn và schema dữ liệu riêng biệt, không chia sẻ database làm cơ chế tích hợp.",
        "code": "backend/ (9 microservices độc lập, cấu trúc Clean Architecture)",
        "db": "7 PostgreSQL Schemas độc lập"
    },
    "NFR-MNT-005": {
        "solution": "Cấu hình hệ thống linh hoạt: Bảng giá, bán kính tìm kiếm, timeout offer, chu kỳ telemetry đều cấu hình qua application.yml / Config Server, không hard-code.",
        "code": "backend/config-service/.../config-repo/ (trip-service.yml, location-service.yml, pricing-service.yml)",
        "db": "Spring Cloud Config Server"
    },
    "NFR-MNT-006": {
        "solution": "Quản lý tiến hóa cơ sở dữ liệu (Database Migration): Sử dụng Flyway / Liquibase với các tệp script có phiên bản (V1__init.sql, V2__indexes.sql).",
        "code": "backend/ (thư mục src/main/resources/db/migration/)",
        "db": "PostgreSQL: bảng flyway_schema_history theo dõi lịch sử migration"
    },

    "NFR-PRV-001": {
        "solution": "Ứng dụng di động giải thích rõ ràng mục đích sử dụng vị trí và yêu cầu cấp quyền Location Permission theo chuẩn của hệ điều hành iOS/Android.",
        "code": "frontend/ (Permission Handler request location when in use / always)",
        "db": "Quy chế bảo mật quyền riêng tư"
    },
    "NFR-PRV-002": {
        "solution": "Hệ thống tuyệt đối không chia sẻ vị trí của tài xế với khách hàng khi chưa được ghép chuyến hoặc sau khi chuyến xe đã kết thúc.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (validateTrackingPermission)",
        "db": "Xác thực quan hệ chuyến active trong trip.trips"
    },
    "NFR-PRV-003": {
        "solution": "Tài xế không có quyền xem thông tin số điện thoại hoặc lịch sử vị trí của những chuyến xe không được gán cho mình.",
        "code": "backend/trip-service/.../service/impl/TripServiceImpl.java (kiểm tra driver_id = current_driver)",
        "db": "trip.trips (WHERE driver_id = :currentDriverId)"
    },
    "NFR-PRV-004": {
        "solution": "Nhân viên chăm sóc khách hàng chỉ được xem vị trí lịch sử của chuyến xe khi có yêu cầu hỗ trợ/khiếu nại; thao tác xem được ghi vết Audit Log.",
        "code": "backend/location-service/.../controller/AdminLocationController.java; AuditLogServiceImpl.java",
        "db": "platform.audit_logs (actor_id, action = 'VIEW_TRIP_TELEMETRY', trip_id, reason)"
    },
    "NFR-PRV-005": {
        "solution": "Dữ liệu vị trí sử dụng cho mục đích phân tích mật độ giao thông và bản đồ nhiệt bắt buộc phải được tổng hợp (Aggregated) hoặc ẩn danh (Anonymized).",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (generateHeatmapData)",
        "db": "Dữ liệu heatmap chỉ gồm tọa độ cell H3 và số lượng xe, loại bỏ hoàn toàn driver_id"
    },
    "NFR-PRV-006": {
        "solution": "Phát hiện tọa độ GPS bất thường hoặc dấu hiệu Fake GPS (tốc độ phi lý, tọa độ nhảy vọt) để gắn cờ xem xét và loại trừ khỏi hệ thống phân bổ cuốc.",
        "code": "backend/location-service/.../service/impl/LocationServiceImpl.java (detectMockLocation)",
        "db": "location.driver_locations (is_suspicious_gps BOOLEAN DEFAULT false)"
    },

    "NFR-COMP-001": {
        "solution": "Duy trì tương thích ngược (Backward Compatibility): Các endpoint API phiên bản hiện tại đảm bảo tương thích với các phiên bản ứng dụng di động cũ hơn trong cùng major version.",
        "code": "backend/common-library/.../dto/ (các trường mới luôn là Optional, không xóa hoặc đổi kiểu trường cũ)",
        "db": "Database schema: các cột mới bổ sung có giá trị DEFAULT hoặc cho phép NULL"
    },
    "NFR-COMP-002": {
        "solution": "Khả năng mở rộng Schema sự kiện Kafka: Consumer cấu hình bỏ qua các trường không xác định (ignore unknown properties), Producer không đổi nghĩa trường bắt buộc.",
        "code": "backend/common-library/.../config/JacksonConfig.java (DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false)",
        "db": "Event Schema Compatibility"
    },
    "NFR-COMP-003": {
        "solution": "Thông báo nâng cấp ứng dụng (App Version Check): Cung cấp API kiểm tra phiên bản ứng dụng; thông báo người dùng nâng cấp khi phiên bản API cũ hết hạn hỗ trợ.",
        "code": "backend/iam-service/.../controller/SystemController.java (checkAppVersion)",
        "db": "platform.system_configs (min_supported_app_version, force_update_flag)"
    }
}
