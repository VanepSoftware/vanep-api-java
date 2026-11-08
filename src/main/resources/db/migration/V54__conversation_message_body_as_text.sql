-- Message bodies are stored encrypted (AES-256-GCM, base64 with a version prefix), which is
-- longer than the 2000-character plaintext limit the API still enforces.

alter table conversation_message alter column body type text;
