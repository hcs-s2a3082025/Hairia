CREATE TABLE salons (
    salon_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    salon_name VARCHAR(150) NOT NULL,
    address VARCHAR(255),
    phone_number VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(20) NOT NULL
        CHECK (user_type IN ('HAIRDRESSER', 'CUSTOMER')),
    account_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hairdressers (
    hairdresser_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (user_id),
    salon_id BIGINT REFERENCES salons (salon_id),
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (user_id),
    display_name VARCHAR(100) NOT NULL,
    birth_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE password_change_histories (
    history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_method VARCHAR(30) NOT NULL
);

CREATE TABLE customer_links (
    link_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hairdresser_id BIGINT NOT NULL REFERENCES hairdressers (hairdresser_id),
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    link_status VARCHAR(20) NOT NULL,
    consented_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_customer_links_active_pair
    ON customer_links (hairdresser_id, customer_id)
    WHERE link_status = 'ACTIVE';

CREATE TABLE qr_codes (
    qr_code_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hairdresser_id BIGINT NOT NULL REFERENCES hairdressers (hairdresser_id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (expires_at > created_at)
);

CREATE TABLE counselings (
    counseling_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    hairdresser_id BIGINT NOT NULL REFERENCES hairdressers (hairdresser_id),
    counseling_type VARCHAR(30) NOT NULL
        CHECK (counseling_type IN ('HAIR', 'COLOR')),
    counseling_status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hair_conditions (
    hair_condition_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    counseling_id BIGINT NOT NULL REFERENCES counselings (counseling_id),
    hair_thickness VARCHAR(30),
    hair_texture VARCHAR(50),
    curl_type VARCHAR(30),
    damage_level SMALLINT CHECK (damage_level BETWEEN 1 AND 5),
    treatment_history TEXT,
    photo_uri TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hair_analysis_results (
    hair_analysis_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hair_condition_id BIGINT NOT NULL REFERENCES hair_conditions (hair_condition_id),
    estimated_damage_level SMALLINT CHECK (estimated_damage_level BETWEEN 1 AND 5),
    estimated_texture VARCHAR(50),
    estimated_curl_type VARCHAR(30),
    analysis_result JSONB,
    model_version VARCHAR(50),
    reviewed_by BIGINT REFERENCES hairdressers (hairdresser_id),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE desired_colors (
    desired_color_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    counseling_id BIGINT NOT NULL REFERENCES counselings (counseling_id),
    color_name VARCHAR(100) NOT NULL,
    color_code VARCHAR(20),
    bleaching_preference BOOLEAN,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE color_analysis_results (
    color_analysis_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    desired_color_id BIGINT NOT NULL REFERENCES desired_colors (desired_color_id),
    feasibility VARCHAR(30) NOT NULL
        CHECK (feasibility IN ('A', 'B', 'C')),
    estimated_sessions SMALLINT CHECK (estimated_sessions > 0),
    analysis_result JSONB,
    cautions TEXT,
    model_version VARCHAR(50),
    reviewed_by BIGINT REFERENCES hairdressers (hairdresser_id),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE treatment_roadmaps (
    roadmap_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    counseling_id BIGINT NOT NULL REFERENCES counselings (counseling_id),
    goal_description TEXT,
    roadmap_status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES hairdressers (hairdresser_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roadmap_details (
    roadmap_detail_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roadmap_id BIGINT NOT NULL REFERENCES treatment_roadmaps (roadmap_id),
    step_number SMALLINT NOT NULL CHECK (step_number >= 1),
    planned_treatment VARCHAR(100) NOT NULL,
    planned_date DATE,
    description TEXT,
    step_status VARCHAR(20) NOT NULL,
    UNIQUE (roadmap_id, step_number)
);

CREATE TABLE charts (
    chart_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    hairdresser_id BIGINT NOT NULL REFERENCES hairdressers (hairdresser_id),
    counseling_id BIGINT REFERENCES counselings (counseling_id),
    chart_title VARCHAR(150) NOT NULL,
    chart_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE treatment_histories (
    treatment_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chart_id BIGINT NOT NULL REFERENCES charts (chart_id),
    treatment_type VARCHAR(50) NOT NULL,
    treatment_date DATE NOT NULL,
    treatment_content TEXT,
    actual_result TEXT,
    created_by BIGINT NOT NULL REFERENCES hairdressers (hairdresser_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE treatment_products (
    product_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    manufacturer VARCHAR(100),
    product_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_mixtures (
    mixture_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    treatment_history_id BIGINT NOT NULL REFERENCES treatment_histories (treatment_history_id),
    product_id BIGINT NOT NULL REFERENCES treatment_products (product_id),
    amount NUMERIC(10, 2) CHECK (amount >= 0),
    amount_unit VARCHAR(20),
    mixing_ratio NUMERIC(7, 3) CHECK (mixing_ratio >= 0),
    notes TEXT
);

CREATE TABLE chat_rooms (
    chat_room_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES customer_links (link_id),
    room_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    message_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_room_id BIGINT NOT NULL REFERENCES chat_rooms (chat_room_id),
    sender_user_id BIGINT NOT NULL REFERENCES users (user_id),
    message_type VARCHAR(20) NOT NULL CHECK (message_type IN ('TEXT', 'IMAGE')),
    message_text TEXT,
    attachment_uri TEXT,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (message_type = 'TEXT' AND message_text IS NOT NULL)
        OR (message_type = 'IMAGE' AND attachment_uri IS NOT NULL)
    )
);

CREATE TABLE message_reads (
    message_read_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES chat_messages (message_id),
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (message_id, user_id)
);

CREATE TABLE reservations (
    reservation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    hairdresser_id BIGINT NOT NULL REFERENCES hairdressers (hairdresser_id),
    salon_id BIGINT NOT NULL REFERENCES salons (salon_id),
    reserved_at TIMESTAMP NOT NULL,
    reservation_status VARCHAR(20) NOT NULL,
    menu_description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE questionnaires (
    questionnaire_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    salon_id BIGINT REFERENCES salons (salon_id),
    question_text TEXT NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE questionnaire_answers (
    answer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    questionnaire_id BIGINT NOT NULL REFERENCES questionnaires (questionnaire_id),
    reservation_id BIGINT NOT NULL REFERENCES reservations (reservation_id),
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    answer_text TEXT,
    answered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    notification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    body TEXT,
    related_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE simulations (
    simulation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    input_photo_uri TEXT NOT NULL,
    hairstyle VARCHAR(100),
    color_code VARCHAR(20),
    result_photo_uri TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE color_diagnosis_results (
    diagnosis_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers (customer_id),
    input_conditions JSONB,
    recommended_colors JSONB NOT NULL,
    diagnosis_comment TEXT,
    model_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chart_shares (
    chart_share_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chart_id BIGINT NOT NULL REFERENCES charts (chart_id),
    shared_by BIGINT NOT NULL REFERENCES users (user_id),
    shared_with BIGINT NOT NULL REFERENCES users (user_id),
    consent_status VARCHAR(20) NOT NULL,
    shared_at TIMESTAMP,
    revoked_at TIMESTAMP
);

CREATE INDEX idx_password_change_histories_user_id
    ON password_change_histories (user_id);
CREATE INDEX idx_customer_links_customer_id
    ON customer_links (customer_id);
CREATE INDEX idx_qr_codes_hairdresser_expires
    ON qr_codes (hairdresser_id, expires_at);
CREATE INDEX idx_counselings_customer_started
    ON counselings (customer_id, started_at DESC);
CREATE INDEX idx_hair_conditions_counseling_id
    ON hair_conditions (counseling_id);
CREATE INDEX idx_hair_analysis_results_condition_id
    ON hair_analysis_results (hair_condition_id);
CREATE INDEX idx_desired_colors_counseling_id
    ON desired_colors (counseling_id);
CREATE INDEX idx_color_analysis_results_desired_color_id
    ON color_analysis_results (desired_color_id);
CREATE INDEX idx_treatment_roadmaps_customer_id
    ON treatment_roadmaps (customer_id);
CREATE INDEX idx_charts_customer_created
    ON charts (customer_id, created_at DESC);
CREATE INDEX idx_treatment_histories_chart_date
    ON treatment_histories (chart_id, treatment_date DESC);
CREATE INDEX idx_product_mixtures_treatment_history_id
    ON product_mixtures (treatment_history_id);
CREATE INDEX idx_chat_rooms_link_id
    ON chat_rooms (link_id);
CREATE INDEX idx_chat_messages_room_sent
    ON chat_messages (chat_room_id, sent_at);
CREATE INDEX idx_message_reads_user_id
    ON message_reads (user_id);
CREATE INDEX idx_reservations_customer_reserved
    ON reservations (customer_id, reserved_at);
CREATE INDEX idx_reservations_hairdresser_reserved
    ON reservations (hairdresser_id, reserved_at);
CREATE INDEX idx_questionnaire_answers_reservation_id
    ON questionnaire_answers (reservation_id);
CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE is_read = FALSE;
CREATE INDEX idx_simulations_customer_created
    ON simulations (customer_id, created_at DESC);
CREATE INDEX idx_color_diagnosis_results_customer_created
    ON color_diagnosis_results (customer_id, created_at DESC);
CREATE INDEX idx_chart_shares_chart_id
    ON chart_shares (chart_id);

CREATE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER salons_set_updated_at
    BEFORE UPDATE ON salons
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER hairdressers_set_updated_at
    BEFORE UPDATE ON hairdressers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER customers_set_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER treatment_roadmaps_set_updated_at
    BEFORE UPDATE ON treatment_roadmaps
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER charts_set_updated_at
    BEFORE UPDATE ON charts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER chat_rooms_set_updated_at
    BEFORE UPDATE ON chat_rooms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
