CREATE TABLE prediction_log (
    id                  BIGSERIAL PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL,
    carrier             VARCHAR(3)  NOT NULL,
    origin              VARCHAR(4)  NOT NULL,
    dest                VARCHAR(4)  NOT NULL,
    flight_date         DATE        NOT NULL,
    dep_hour            INT         NOT NULL,
    prev_leg_arr_delay  DOUBLE PRECISION,
    probability         DOUBLE PRECISION NOT NULL,
    predicted_delayed   BOOLEAN     NOT NULL,
    cache_hit           BOOLEAN     NOT NULL,
    latency_micros      BIGINT      NOT NULL,
    model_version       VARCHAR(80) NOT NULL
);
CREATE INDEX idx_prediction_log_created_at ON prediction_log (created_at DESC);
CREATE INDEX idx_prediction_log_route ON prediction_log (origin, dest);
