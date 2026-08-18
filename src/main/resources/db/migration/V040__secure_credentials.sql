-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 23.18 QR permanent + medium NFC futur sans changer le coeur métier
-- --------------------------------------------------------------------------

ALTER TABLE qr_credentials ADD COLUMN medium VARCHAR(20) NOT NULL DEFAULT 'QR';
ALTER TABLE qr_credentials ADD COLUMN used_at TIMESTAMPTZ;
ALTER TABLE qr_credentials ADD CONSTRAINT ck_qr_credentials_medium CHECK (
    medium IN ('QR','NFC','TOKEN')
);

-- --------------------------------------------------------------------------
