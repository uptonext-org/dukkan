-- B-53: rate-limit counters shared by all instances (fixed windows aligned to the epoch). Rows are
-- removed after expires_at by RateLimitCleanupJob.
CREATE TABLE rate_limit_counter (
    bucket       varchar(40)  NOT NULL,
    key          varchar(200) NOT NULL,
    window_start timestamptz  NOT NULL,
    count        integer      NOT NULL,
    expires_at   timestamptz  NOT NULL,
    PRIMARY KEY (bucket, key, window_start)
);
CREATE INDEX ix_rate_limit_counter_expires ON rate_limit_counter (expires_at);
