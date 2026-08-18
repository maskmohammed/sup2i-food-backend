-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.24 Prévision demande / rupture / production future
-- --------------------------------------------------------------------------

CREATE TABLE demand_forecasts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id        UUID NOT NULL REFERENCES locations(id),
    product_id         UUID REFERENCES products(id),
    ingredient_id      UUID REFERENCES ingredients(id),
    forecast_date      DATE NOT NULL,
    time_slot_id       UUID REFERENCES time_slots(id),
    predicted_quantity NUMERIC(14,3) NOT NULL,
    confidence_score   NUMERIC(6,5),
    model_name         VARCHAR(120),
    model_version      VARCHAR(80),
    features_snapshot  JSONB,
    generated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_demand_forecasts_subject CHECK (
        num_nonnulls(product_id, ingredient_id) = 1
    ),
    CONSTRAINT ck_demand_forecasts_quantity CHECK (predicted_quantity >= 0),
    CONSTRAINT ck_demand_forecasts_confidence CHECK (
        confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1
    )
);

CREATE INDEX idx_demand_forecasts_lookup
    ON demand_forecasts(location_id, forecast_date, product_id, ingredient_id);

-- --------------------------------------------------------------------------
