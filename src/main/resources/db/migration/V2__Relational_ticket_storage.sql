ALTER TABLE departments ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE ticket_records (
 id CHAR(36) PRIMARY KEY, revision BIGINT NOT NULL,
 legacy_version BIGINT,
 patient_code TEXT NOT NULL,
 origin_id CHAR(36) NOT NULL, origin_name VARCHAR(255) NOT NULL,
 destination_id CHAR(36) NOT NULL, destination_name VARCHAR(255) NOT NULL,
 creator_id CHAR(36) NOT NULL, creator_name VARCHAR(100) NOT NULL, creator_department_id CHAR(36),
 priority VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL,
 assigned_operator_id CHAR(36), assigned_operator_name VARCHAR(100),
 created_second BIGINT NOT NULL, created_nano INT NOT NULL,
 scheduled_second BIGINT, scheduled_nano INT,
 first_assigned_second BIGINT, first_assigned_nano INT,
 completed_second BIGINT, completed_nano INT,
 event_count INT NOT NULL,
 FOREIGN KEY (origin_id) REFERENCES departments(id),
 FOREIGN KEY (destination_id) REFERENCES departments(id),
 INDEX idx_ticket_origin_status (origin_id,status),
 INDEX idx_ticket_operator_status (assigned_operator_id,status),
 CHECK (priority IN ('NORMAL','URGENT')),
 CHECK (status IN ('UNASSIGNED','ASSIGNED','ACCEPTED','STARTED','PATIENT_IDENTIFIED','IN_TRANSIT','ARRIVED','COMPLETED')),
 CHECK (created_nano BETWEEN 0 AND 999999999),
 CHECK ((scheduled_second IS NULL AND scheduled_nano IS NULL) OR (scheduled_second IS NOT NULL AND scheduled_nano IS NOT NULL AND scheduled_nano BETWEEN 0 AND 999999999)),
 CHECK ((first_assigned_second IS NULL AND first_assigned_nano IS NULL) OR (first_assigned_second IS NOT NULL AND first_assigned_nano IS NOT NULL AND first_assigned_nano BETWEEN 0 AND 999999999)),
 CHECK ((completed_second IS NULL AND completed_nano IS NULL) OR (completed_second IS NOT NULL AND completed_nano IS NOT NULL AND completed_nano BETWEEN 0 AND 999999999)),
 CHECK ((assigned_operator_id IS NULL) = (assigned_operator_name IS NULL)),
 CHECK (event_count > 0)
) ENGINE=InnoDB;
CREATE TABLE ticket_events (
 ticket_id CHAR(36) NOT NULL, event_index INT NOT NULL,
 occurred_second BIGINT NOT NULL, occurred_nano INT NOT NULL,
 actor_id CHAR(36) NOT NULL, action VARCHAR(40) NOT NULL,
 resulting_status VARCHAR(30) NOT NULL, assigned_operator_id CHAR(36),
 PRIMARY KEY (ticket_id,event_index),
 FOREIGN KEY (ticket_id) REFERENCES ticket_records(id),
 CHECK (event_index >= 0), CHECK (occurred_nano BETWEEN 0 AND 999999999)
) ENGINE=InnoDB;
