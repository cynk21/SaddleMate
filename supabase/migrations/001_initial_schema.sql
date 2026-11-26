-- ============================================================================
-- MASTER SCHEMA: EQUESTRIAN ROUTING, ADIDAS-TRACKING, VOICE RADAR & WELFARE
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. PFERDE-PROFILE (Steuert Routing, Rehe-Schutz & Hufschmied-Zähler)
CREATE TABLE horses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    experience_level TEXT CHECK (experience_level IN ('jungpferd', 'sicher', 'distanz', 'reha')) DEFAULT 'sicher',
    traffic_safe BOOLEAN DEFAULT false,
    laminitis_risk BOOLEAN DEFAULT false,          -- Rehe-gefährdet (aktiviert Fruktan- & Hartboden-Warnung)
    preferred_surfaces TEXT[] DEFAULT ARRAY['waldboden', 'wiese', 'sand'],
    avoid_surfaces TEXT[] DEFAULT ARRAY['asphalt', 'schotter_scharf', 'matsch_tief'],
    last_farrier_date DATE DEFAULT CURRENT_DATE,   -- Letzter Hufschmied-Termin
    farrier_interval_km FLOAT DEFAULT 250.0,       -- Warn-Schwelle in km
    cumulative_km_since_farrier FLOAT DEFAULT 0.0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. STRECKENNETZ & SEGMENTE (Bodenbeschaffenheit & Gangart-Eignung)
CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID REFERENCES auth.users(id),
    title TEXT NOT NULL,
    description TEXT,
    path GEOMETRY(LineString, 4326) NOT NULL,
    distance_meters FLOAT NOT NULL,
    duration_seconds INT,
    difficulty TEXT CHECK (difficulty IN ('anfaenger', 'fortgeschritten', 'profi')),
    spook_level INT CHECK (spook_level BETWEEN 1 AND 5) DEFAULT 1,
    has_roads BOOLEAN DEFAULT false,
    is_public BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE trail_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geom GEOMETRY(LineString, 4326) NOT NULL,
    surface_type TEXT CHECK (surface_type IN ('asphalt', 'schotter', 'waldboden', 'sand', 'wiese', 'matsch_tief')),
    best_gait TEXT CHECK (best_gait IN ('schritt', 'trab', 'galopp', 'alle')),
    laminitis_safe BOOLEAN DEFAULT false,
    verified_count INT DEFAULT 1,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. COMMUNITY-PINS (Dorf-Etikette, Gefahren, Galoppstrecken)
CREATE TABLE map_pins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID REFERENCES auth.users(id),
    location GEOMETRY(Point, 4326) NOT NULL,
    pin_type TEXT CHECK (pin_type IN (
        'galoppstrecke',
        'gefahr',
        'anwohner_meckert',
        'aepfel_wegmachen',
        'buergersteig_nutzen',
        'strasse_nutzen',
        'tiefer_boden',
        'wasserstelle'
    )),
    comment TEXT,
    upvotes INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. ADIDAS-STYLE RIDE SESSIONS & TELEMETRIE
CREATE TABLE ride_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    horse_id UUID REFERENCES horses(id) ON DELETE SET NULL,
    route_id UUID REFERENCES routes(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    total_duration_sec INT NOT NULL,
    moving_duration_sec INT NOT NULL,
    distance_meters FLOAT NOT NULL,
    avg_speed_kmh FLOAT NOT NULL,
    max_speed_kmh FLOAT NOT NULL,
    elevation_gain_m FLOAT DEFAULT 0,
    elevation_loss_m FLOAT DEFAULT 0,
    time_halt_sec INT DEFAULT 0,
    time_walk_sec INT DEFAULT 0,
    time_trot_sec INT DEFAULT 0,
    time_canter_sec INT DEFAULT 0,
    dist_walk_m FLOAT DEFAULT 0,
    dist_trot_m FLOAT DEFAULT 0,
    dist_canter_m FLOAT DEFAULT 0,
    rider_calories INT,
    horse_energy_mcal FLOAT,
    gps_track GEOMETRY(LineStringZM, 4326),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE ride_telemetry_points (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID REFERENCES ride_sessions(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL,
    location GEOMETRY(PointZ, 4326) NOT NULL,
    speed_kmh FLOAT NOT NULL,
    accuracy_m FLOAT,
    detected_gait TEXT CHECK (detected_gait IN ('halt', 'schritt', 'trab', 'galopp')),
    gait_symmetry_delta FLOAT,
    rider_hr_bpm INT,
    horse_hr_bpm INT
);

CREATE TABLE ride_km_splits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES ride_sessions(id) ON DELETE CASCADE,
    kilometer_number INT NOT NULL,
    duration_sec INT NOT NULL,
    avg_speed_kmh FLOAT NOT NULL,
    elevation_diff_m FLOAT DEFAULT 0,
    dominant_gait TEXT CHECK (dominant_gait IN ('schritt', 'trab', 'galopp'))
);

-- 5. TIERWOHL & VITAL-LOG (Sensorlos ab Tag 1 + Sensor-Ready für später)
CREATE TABLE horse_welfare_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES ride_sessions(id) ON DELETE CASCADE,
    horse_id UUID REFERENCES horses(id) ON DELETE CASCADE,
    warmup_walk_minutes FLOAT NOT NULL,
    cooldown_walk_minutes FLOAT NOT NULL,
    hard_surface_fast_meters FLOAT DEFAULT 0,
    deep_mud_meters FLOAT DEFAULT 0,
    tendon_load_score INT CHECK (tendon_load_score BETWEEN 1 AND 100),
    thermal_stress_index FLOAT,
    fructan_risk_level TEXT CHECK (fructan_risk_level IN ('niedrig', 'mittel', 'hoch')),
    trot_symmetry_pct FLOAT DEFAULT 100.0,
    avg_horse_hr_bpm INT,
    max_horse_hr_bpm INT,
    recovery_hr_after_2min INT,
    welfare_status TEXT CHECK (welfare_status IN ('optimal', 'achtung_belastung', 'erholung_empfohlen')),
    welfare_recommendations TEXT[],
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. NOTFALL- & STURZERKENNUNG ("SADDLE GUARD")
CREATE TABLE emergency_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    contact_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    auto_notify_on_fall BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE safety_incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES ride_sessions(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id),
    detected_at TIMESTAMPTZ DEFAULT NOW(),
    location GEOMETRY(Point, 4326) NOT NULL,
    impact_g_force FLOAT,
    status TEXT CHECK (status IN ('countdown_active', 'cancelled_by_rider', 'sos_dispatched')),
    dispatched_at TIMESTAMPTZ
);

-- 7. AUTONOMES FEEDBACK- & BACKLOG-SYSTEM
CREATE TABLE product_backlog_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    technical_spec TEXT,
    category TEXT CHECK (category IN ('bug', 'ux_improvement', 'new_feature', 'map_logic', 'welfare_sensor')),
    request_count INT DEFAULT 1,
    impact_score INT CHECK (impact_score BETWEEN 1 AND 10) DEFAULT 5,
    complexity_score INT CHECK (complexity_score BETWEEN 1 AND 10) DEFAULT 5,
    priority_score FLOAT GENERATED ALWAYS AS ((request_count * impact_score)::float / GREATEST(complexity_score, 1)) STORED,
    pipeline_status TEXT DEFAULT 'new_triage' CHECK (pipeline_status IN (
        'new_triage', 'backlog_ranked', 'ready_for_hermes', 'in_automated_dev', 'shipped_ota'
    )),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE user_feedback_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    linked_ticket_id UUID REFERENCES product_backlog_tickets(id) ON DELETE SET NULL,
    raw_input TEXT NOT NULL,
    source_context TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. POSTGIS RPC: "BLITZER-RADAR" (Sucht Gefahren/Etikette im 60°-Kegel vor dem Reiter)
CREATE OR REPLACE FUNCTION get_upcoming_alerts(
    curr_lat FLOAT,
    curr_lng FLOAT,
    rider_heading_deg FLOAT,
    lookahead_meters FLOAT DEFAULT 250
)
RETURNS TABLE (
    pin_id UUID,
    pin_type TEXT,
    comment TEXT,
    distance_m FLOAT,
    bearing_deg FLOAT
) AS $$
DECLARE rider_point GEOMETRY;
BEGIN
    rider_point := ST_SetSRID(ST_MakePoint(curr_lng, curr_lat), 4326);
    RETURN QUERY
    SELECT mp.id AS pin_id, mp.pin_type, mp.comment,
           ST_Distance(mp.location::geography, rider_point::geography) AS distance_m,
           degrees(ST_Azimuth(rider_point, mp.location)) AS bearing_deg
    FROM map_pins mp
    WHERE mp.is_active = true
      AND ST_DWithin(mp.location::geography, rider_point::geography, lookahead_meters)
      AND ABS(((degrees(ST_Azimuth(rider_point, mp.location)) - rider_heading_deg + 540)::numeric % 360) - 180) <= 60
    ORDER BY distance_m ASC;
END;
$$ LANGUAGE plpgsql;
