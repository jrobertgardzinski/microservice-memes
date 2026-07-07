-- Admin-tunable runtime settings, one key/value row each — deliberately generic so the next
-- operator dial (whatever it is) needs no migration. First tenant: the purge-policy override
-- ('purge.memes'), which an ADMIN sets over the deployment default without a redeploy.
CREATE TABLE settings (
    setting_key   VARCHAR(64)  PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    updated_by    VARCHAR(255) NOT NULL
);
