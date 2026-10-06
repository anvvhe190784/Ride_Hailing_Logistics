-- ============================================================================
-- TEST SCRIPT: verify_db.sql
-- PURPOSE: Automated verification of tables, constraints, PostGIS and Concurrency
-- ============================================================================

\set ON_ERROR_STOP on

-- 1. Verify Installed Extensions
SELECT extname, extversion
FROM pg_extension
WHERE extname IN ('postgis', 'uuid-ossp');

-- 2. Verify Table Counts per Schema
SELECT table_schema, count(*) as table_count
FROM information_schema.tables
WHERE table_schema IN ('iam', 'driver', 'location', 'pricing', 'trip', 'billing', 'platform')
GROUP BY table_schema
ORDER BY table_schema;

-- 3. Verify PostGIS Spatial Functionality (WGS84 4326 distance in meters)
DO
$$
    DECLARE
        p_pickup    GEOMETRY := ST_SetSRID(ST_MakePoint(106.699, 10.775), 4326); -- Ben Thanh, HCMC
        p_driver    GEOMETRY := ST_SetSRID(ST_MakePoint(106.7009, 10.7769), 4326); -- Driver nearby
        dist_meters DOUBLE PRECISION;
    BEGIN
        dist_meters := ST_Distance(p_pickup::geography, p_driver::geography);
        RAISE NOTICE 'Calculated Spatial Distance: % meters', ROUND(dist_meters::numeric, 2);
        IF dist_meters > 500 THEN
            RAISE EXCEPTION 'Spatial distance test failed!';
        END IF;
    END
$$;

-- 4. Test Concurrency Constraint: Partial Unique Index on Active Driver Trip (BR-002, CON-06, UC-06)
DO
$$
    DECLARE
        v_cust_id          UUID;
        v_driver_id        UUID;
        v_quote_id         UUID;
        v_trip_1           UUID;
        v_trip_2           UUID;
        v_caught_exception BOOLEAN := FALSE;
    BEGIN
        -- Setup temporary test records
        INSERT INTO iam.users (id, phone, email, password_hash, full_name, role)
        VALUES (uuid_generate_v4(), '0900000001', 'cust@test.com', 'hash', 'Test Customer', 'CUSTOMER')
        RETURNING id INTO v_cust_id;

        INSERT INTO iam.users (id, phone, email, password_hash, full_name, role)
        VALUES (uuid_generate_v4(), '0900000002', 'driver@test.com', 'hash', 'Test Driver', 'DRIVER')
        RETURNING id INTO v_driver_id;

        INSERT INTO driver.driver_profiles (driver_id, review_status, availability_status)
        VALUES (v_driver_id, 'APPROVED', 'BUSY');

        INSERT INTO pricing.fare_quotes (id, customer_id, service_type, pickup_point, pickup_address, dropoff_point,
                                         dropoff_address,
                                         estimated_distance_meters, estimated_duration_seconds, base_price,
                                         distance_price, duration_price,
                                         total_fare, rule_version, expires_at)
        VALUES (uuid_generate_v4(), v_cust_id, 'RIDE',
                ST_SetSRID(ST_MakePoint(106.699, 10.775), 4326), 'Pickup',
                ST_SetSRID(ST_MakePoint(106.705, 10.780), 4326), 'Dropoff',
                1000, 300, 15000, 10000, 5000, 30000, 1, NOW() + INTERVAL '10 minutes')
        RETURNING id INTO v_quote_id;

        -- Trip 1: Active trip assigned to driver
        INSERT INTO trip.trips (id, trip_code, service_type, customer_id, driver_id, status, quote_id,
                                idempotency_key, quote_snapshot, payment_method)
        VALUES (uuid_generate_v4(), 'TRP-TEST-001', 'RIDE', v_cust_id, v_driver_id, 'ACCEPTED', v_quote_id,
                'IDEMP-TEST-001', '{
            "total": 30000
          }'::jsonb, 'CASH')
        RETURNING id INTO v_trip_1;

        -- Trip 2: Attempting to assign same driver concurrently in active state (MUST FAIL)
        BEGIN
            INSERT INTO trip.trips (id, trip_code, service_type, customer_id, driver_id, status, quote_id,
                                    idempotency_key, quote_snapshot, payment_method)
            VALUES (uuid_generate_v4(), 'TRP-TEST-002', 'RIDE', v_cust_id, v_driver_id, 'PICKING_UP', v_quote_id,
                    'IDEMP-TEST-002', '{
                "total": 30000
              }'::jsonb, 'CASH')
            RETURNING id INTO v_trip_2;
        EXCEPTION
            WHEN unique_violation THEN
                v_caught_exception := TRUE;
                RAISE NOTICE 'SUCCESS: Concurrency Partial Unique Index correctly blocked double assignment!';
        END;

        IF NOT v_caught_exception THEN
            RAISE EXCEPTION 'FAILED: Driver was illegally assigned to two concurrent active trips!';
        END IF;

        -- Clean up test records
        DELETE FROM trip.trips WHERE id = v_trip_1;
        DELETE FROM pricing.fare_quotes WHERE id = v_quote_id;
        DELETE FROM driver.driver_profiles WHERE driver_id = v_driver_id;
        DELETE FROM iam.users WHERE id IN (v_cust_id, v_driver_id);
    END
$$;
