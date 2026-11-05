-- ----------------------------------------------------------------------------
-- NOTIFICATION LOG
-- Invariant: each notification goes out at most once. A sender claims its
-- (type, ref) row before sending, so a restart, the startup catch-up and the
-- scheduled run can never send the same digest twice. ref is the day for a
-- daily digest (2026-11-03).
-- ----------------------------------------------------------------------------

CREATE TABLE notification_log (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type    TEXT NOT NULL,
    ref     TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (type, ref)
);
