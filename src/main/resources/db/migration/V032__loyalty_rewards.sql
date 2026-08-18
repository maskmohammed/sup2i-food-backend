-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.10 Fidélité : expiration + récompenses
-- --------------------------------------------------------------------------

ALTER TABLE loyalty_accounts ADD COLUMN lifetime_earned INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loyalty_accounts ADD COLUMN lifetime_redeemed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loyalty_accounts ADD CONSTRAINT ck_loyalty_accounts_lifetime CHECK (
    lifetime_earned >= 0 AND lifetime_redeemed >= 0
);

ALTER TABLE loyalty_transactions DROP CONSTRAINT ck_loyalty_transactions_type;
ALTER TABLE loyalty_transactions ADD CONSTRAINT ck_loyalty_transactions_type CHECK (
    type IN ('EARN','REDEEM','BONUS','ADJUSTMENT','EXPIRE','REVERSAL')
);

CREATE TABLE loyalty_rewards (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code            VARCHAR(80) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    points_cost     INTEGER NOT NULL,
    reward_type     VARCHAR(30) NOT NULL,
    reward_config   JSONB NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    CONSTRAINT uq_loyalty_rewards_org_code UNIQUE(organization_id, code),
    CONSTRAINT ck_loyalty_rewards_points CHECK (points_cost > 0),
    CONSTRAINT ck_loyalty_rewards_type CHECK (
        reward_type IN ('PRODUCT','DISCOUNT','COUPON','OTHER')
    )
);

ALTER TABLE loyalty_transactions ADD COLUMN reward_id UUID REFERENCES loyalty_rewards(id);
ALTER TABLE loyalty_transactions ADD COLUMN order_id UUID REFERENCES orders(id);
ALTER TABLE loyalty_transactions ADD COLUMN refund_id UUID REFERENCES refunds(id);

-- --------------------------------------------------------------------------
