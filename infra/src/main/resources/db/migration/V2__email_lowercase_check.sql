-- Phase 3 Gate 3 / review finding I1 — defense in depth for case-insensitive
-- email uniqueness. The domain layer (`User.register`) lower-cases + trims
-- email before construction; this constraint blocks any future adapter, raw
-- jOOQ DSL path, or bulk import that bypasses the aggregate.

ALTER TABLE users
    ADD CONSTRAINT users_email_lowercase CHECK (email = lower(email));
