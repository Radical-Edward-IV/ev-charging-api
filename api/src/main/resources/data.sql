-- Fallback sample data for Seoul EV charging stations
-- Only loaded when spring.sql.init.mode=always (local profile)

INSERT INTO charging_station (station_code, name, address, latitude, longitude, operator_name, contact_number, operating_hours, created_at, updated_at)
VALUES
    ('ST-SAMPLE-001', '강남역 충전소', '서울특별시 강남구 강남대로 396', 37.4979, 127.0276, '한국전력', '02-1234-5678', '24시간', NOW(), NOW()),
    ('ST-SAMPLE-002', '서울역 충전소', '서울특별시 용산구 한강대로 405', 37.5547, 126.9707, '환경부', '02-2345-6789', '06:00~23:00', NOW(), NOW()),
    ('ST-SAMPLE-003', '여의도 충전소', '서울특별시 영등포구 여의대로 108', 37.5219, 126.9245, '한국전력', '02-3456-7890', '24시간', NOW(), NOW()),
    ('ST-SAMPLE-004', '잠실역 충전소', '서울특별시 송파구 올림픽로 300', 37.5133, 127.1001, 'SK에너지', '02-4567-8901', '05:00~24:00', NOW(), NOW()),
    ('ST-SAMPLE-005', '홍대입구 충전소', '서울특별시 마포구 양화로 188', 37.5571, 126.9246, 'GS칼텍스', '02-5678-9012', '24시간', NOW(), NOW());

INSERT INTO charger (station_id, charger_code, type, status, power_kw, connector_type, last_status_changed_at)
VALUES
    (1, 'CHG-001-01', 'DC_FAST', 'AVAILABLE', 50, 'CCS1', NOW()),
    (1, 'CHG-001-02', 'AC_SLOW', 'AVAILABLE', 7, 'AC_TYPE_1', NOW()),
    (2, 'CHG-002-01', 'DC_COMBO', 'AVAILABLE', 100, 'CCS1', NOW()),
    (2, 'CHG-002-02', 'DC_FAST', 'OUT_OF_SERVICE', 50, 'CHADEMO', NOW()),
    (3, 'CHG-003-01', 'AC_SLOW', 'AVAILABLE', 7, 'AC_TYPE_1', NOW()),
    (3, 'CHG-003-02', 'DC_FAST', 'AVAILABLE', 50, 'CCS1', NOW()),
    (4, 'CHG-004-01', 'DC_COMBO', 'AVAILABLE', 100, 'CCS1', NOW()),
    (4, 'CHG-004-02', 'AC_SLOW', 'CHARGING', 7, 'AC_TYPE_3', NOW()),
    (5, 'CHG-005-01', 'DC_FAST', 'AVAILABLE', 50, 'CHADEMO', NOW()),
    (5, 'CHG-005-02', 'AC_SLOW', 'AVAILABLE', 7, 'AC_TYPE_1', NOW());
