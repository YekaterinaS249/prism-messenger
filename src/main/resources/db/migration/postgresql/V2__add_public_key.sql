-- End-to-end encryption: each user publishes an ECDH (P-256) public key so peers can derive a
-- shared AES-GCM key entirely client-side. The server only ever stores/relays the public key and
-- ciphertext — it never sees plaintext message content or private keys.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS public_key varchar(255);
