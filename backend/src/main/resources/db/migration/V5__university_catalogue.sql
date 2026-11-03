ALTER TABLE universities ADD region VARCHAR(100);
ALTER TABLE universities ADD license_source VARCHAR(1000);
ALTER TABLE universities ADD catalogue_checked_on VARCHAR(10);
ALTER TABLE universities ADD programs LONGTEXT;
CREATE TABLE data_revisions (id VARCHAR(100) PRIMARY KEY, created_at VARCHAR(30) NOT NULL, details TEXT);
