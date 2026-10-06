-- ============================================================================
-- SCRIPT: init_db.sql
-- PURPOSE: Create ride_hailing_db database if it does not already exist
-- ============================================================================

SELECT 'CREATE DATABASE ride_hailing_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ride_hailing_db')
\gexec
