ALTER TABLE bulletins ADD processing_status VARCHAR(30) NOT NULL DEFAULT 'ARCHIVO';
ALTER TABLE bulletins ADD processing_error TEXT;
ALTER TABLE bulletins ADD question_count INT NOT NULL DEFAULT 0;
ALTER TABLE bulletins ADD processed_count INT NOT NULL DEFAULT 0;
ALTER TABLE bulletins ADD processing_owner VARCHAR(36);
ALTER TABLE bulletins ADD processing_until VARCHAR(30);
ALTER TABLE bulletins ADD uploaded_by VARCHAR(36);
CREATE TABLE bulletin_questions (
 id VARCHAR(36) PRIMARY KEY, created_at VARCHAR(30) NOT NULL,
 bulletin_id VARCHAR(36) NOT NULL, position INT NOT NULL, source_number INT NOT NULL,
 prompt LONGTEXT NOT NULL, options LONGTEXT NOT NULL, images LONGTEXT NOT NULL,
 source_text LONGTEXT NOT NULL, answer VARCHAR(2), explanation LONGTEXT,
 course_name VARCHAR(150) NOT NULL DEFAULT 'General',
 solution_status VARCHAR(24) NOT NULL DEFAULT 'PENDIENTE', model VARCHAR(100), failure_count INT NOT NULL DEFAULT 0,
 FOREIGN KEY(bulletin_id) REFERENCES bulletins(id), UNIQUE(bulletin_id,position)
);
CREATE TABLE bulletin_attempts (
 id VARCHAR(36) PRIMARY KEY, created_at VARCHAR(30) NOT NULL,
 bulletin_id VARCHAR(36) NOT NULL, student_id VARCHAR(36) NOT NULL,
 questions LONGTEXT NOT NULL, answers LONGTEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'EN_CURSO', submitted_at VARCHAR(30),
 grade DECIMAL(5,2), correct_count INT, graded_count INT,
 FOREIGN KEY(bulletin_id) REFERENCES bulletins(id), FOREIGN KEY(student_id) REFERENCES users(id)
);
CREATE INDEX idx_bulletin_attempts_student ON bulletin_attempts(student_id,bulletin_id);
