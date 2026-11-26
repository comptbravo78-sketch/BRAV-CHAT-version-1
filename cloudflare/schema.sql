-- BRAV Chat Cloudflare D1 Schema (database: brav_chat / 005a0119-2bb4-40ce-a50c-b3c3a461bc19)

CREATE TABLE IF NOT EXISTS users_index (
  user_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL DEFAULT '',
  username TEXT NOT NULL DEFAULT '',
  location_city TEXT NOT NULL DEFAULT '',
  avatar_url TEXT NOT NULL DEFAULT '',
  online INTEGER NOT NULL DEFAULT 0,
  last_seen_ms INTEGER NOT NULL DEFAULT 0,
  updated_at_ms INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users_index(username);
CREATE INDEX IF NOT EXISTS idx_users_display_name ON users_index(display_name);

CREATE TABLE IF NOT EXISTS offers_index (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  title TEXT NOT NULL,
  category TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  location_city TEXT NOT NULL DEFAULT '',
  price_or_salary TEXT NOT NULL DEFAULT 'Gratuit',
  author_name TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'published',
  payment_status TEXT NOT NULL DEFAULT 'free',
  created_at_ms INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_offers_category ON offers_index(category);
CREATE INDEX IF NOT EXISTS idx_offers_city ON offers_index(location_city);

CREATE TABLE IF NOT EXISTS media_objects (
  object_key TEXT PRIMARY KEY,
  owner_uid TEXT NOT NULL,
  conversation_id TEXT NOT NULL,
  file_name TEXT NOT NULL DEFAULT '',
  content_type TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  created_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_media_conv ON media_objects(conversation_id);
CREATE INDEX IF NOT EXISTS idx_media_owner ON media_objects(owner_uid);

CREATE TABLE IF NOT EXISTS device_tokens (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  platform TEXT NOT NULL DEFAULT 'android',
  updated_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_devices_user ON device_tokens(user_id);

CREATE TABLE IF NOT EXISTS call_sfu_sessions (
  call_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  tracks_json TEXT NOT NULL DEFAULT '[]',
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (call_id, session_id)
);

CREATE TABLE IF NOT EXISTS blocked_users (
  blocker_uid TEXT NOT NULL,
  blocked_uid TEXT NOT NULL,
  reason TEXT NOT NULL DEFAULT '',
  created_at_ms INTEGER NOT NULL,
  PRIMARY KEY (blocker_uid, blocked_uid)
);

CREATE INDEX IF NOT EXISTS idx_blocked_target ON blocked_users(blocked_uid);

CREATE TABLE IF NOT EXISTS deleted_messages (
  user_id TEXT NOT NULL,
  conversation_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  scope TEXT NOT NULL DEFAULT 'me',
  deleted_at_ms INTEGER NOT NULL,
  PRIMARY KEY (user_id, message_id, scope)
);

CREATE INDEX IF NOT EXISTS idx_deleted_msg_conv ON deleted_messages(conversation_id);

CREATE TABLE IF NOT EXISTS conversation_user_states (
  user_id TEXT NOT NULL,
  conversation_id TEXT NOT NULL,
  archived INTEGER NOT NULL DEFAULT 0,
  pinned INTEGER NOT NULL DEFAULT 0,
  muted INTEGER NOT NULL DEFAULT 0,
  locked INTEGER NOT NULL DEFAULT 0,
  marked_unread INTEGER NOT NULL DEFAULT 0,
  hidden_locally INTEGER NOT NULL DEFAULT 0,
  last_read_at_ms INTEGER NOT NULL DEFAULT 0,
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (user_id, conversation_id)
);

CREATE TABLE IF NOT EXISTS user_contacts (
  owner_uid TEXT NOT NULL,
  contact_id TEXT NOT NULL,
  display_name TEXT NOT NULL DEFAULT '',
  normalized_phone TEXT NOT NULL DEFAULT '',
  brav_uid TEXT NOT NULL DEFAULT '',
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (owner_uid, contact_id)
);

CREATE INDEX IF NOT EXISTS idx_user_contacts_owner ON user_contacts(owner_uid);

CREATE TABLE IF NOT EXISTS privacy_settings (
  user_id TEXT PRIMARY KEY,
  show_photo TEXT NOT NULL DEFAULT 'everyone',
  show_status TEXT NOT NULL DEFAULT 'everyone',
  show_last_seen TEXT NOT NULL DEFAULT 'everyone',
  who_can_call TEXT NOT NULL DEFAULT 'everyone',
  read_receipts INTEGER NOT NULL DEFAULT 1,
  updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS login_history (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  device_id TEXT NOT NULL DEFAULT '',
  device_name TEXT NOT NULL DEFAULT '',
  country_code TEXT NOT NULL DEFAULT '',
  city TEXT NOT NULL DEFAULT '',
  created_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_login_history_user ON login_history(user_id, created_at_ms DESC);

CREATE TABLE IF NOT EXISTS ai_response_cache (
  cache_key TEXT PRIMARY KEY,
  model TEXT NOT NULL,
  response_text TEXT NOT NULL,
  created_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS auto_reply_rules (
  user_id TEXT NOT NULL,
  contact_key TEXT NOT NULL,
  contact_label TEXT NOT NULL DEFAULT '',
  mode TEXT NOT NULL DEFAULT 'auto',
  tone TEXT NOT NULL DEFAULT 'professionnel',
  custom_instruction TEXT NOT NULL DEFAULT '',
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (user_id, contact_key)
);

CREATE INDEX IF NOT EXISTS idx_auto_reply_rules_user ON auto_reply_rules(user_id);

CREATE TABLE IF NOT EXISTS ai_usage_metrics (
  user_id TEXT NOT NULL,
  day_key TEXT NOT NULL,
  request_count INTEGER NOT NULL DEFAULT 0,
  last_model TEXT NOT NULL DEFAULT '',
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (user_id, day_key)
);

CREATE TABLE IF NOT EXISTS documents_meta (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  title TEXT NOT NULL,
  doc_format TEXT NOT NULL,
  template_type TEXT NOT NULL DEFAULT 'rapport',
  object_key TEXT NOT NULL DEFAULT '',
  size_bytes INTEGER NOT NULL DEFAULT 0,
  created_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_documents_user ON documents_meta(user_id, created_at_ms DESC);

CREATE TABLE IF NOT EXISTS launcher_profiles (
  user_id TEXT PRIMARY KEY,
  theme_id TEXT NOT NULL DEFAULT 'brav_signature',
  icon_shape TEXT NOT NULL DEFAULT 'squircle',
  config_json TEXT NOT NULL DEFAULT '{}',
  updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS user_follows (
  target_uid TEXT NOT NULL,
  follower_uid TEXT NOT NULL,
  created_at_ms INTEGER NOT NULL,
  PRIMARY KEY (target_uid, follower_uid)
);

CREATE INDEX IF NOT EXISTS idx_follows_target ON user_follows(target_uid);
CREATE INDEX IF NOT EXISTS idx_follows_follower ON user_follows(follower_uid);

CREATE TABLE IF NOT EXISTS certification_cases (
  case_number TEXT PRIMARY KEY,
  user_id TEXT NOT NULL UNIQUE,
  category TEXT NOT NULL DEFAULT 'creator',
  followers_count INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'none',
  selfie_key TEXT NOT NULL DEFAULT '',
  selfie_score REAL NOT NULL DEFAULT 0,
  liveness_passed INTEGER NOT NULL DEFAULT 0,
  spoof_risk TEXT NOT NULL DEFAULT 'unknown',
  terms_accepted INTEGER NOT NULL DEFAULT 0,
  selfie_consent INTEGER NOT NULL DEFAULT 0,
  payment_tx_ref TEXT NOT NULL DEFAULT '',
  payment_amount_xof INTEGER NOT NULL DEFAULT 0,
  payment_currency TEXT NOT NULL DEFAULT 'XOF',
  receipt_number TEXT NOT NULL DEFAULT '',
  designer_exempt INTEGER NOT NULL DEFAULT 0,
  reviewer_note TEXT NOT NULL DEFAULT '',
  created_at_ms INTEGER NOT NULL,
  updated_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cert_cases_user ON certification_cases(user_id);

CREATE TABLE IF NOT EXISTS certification_payments (
  tx_ref TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  user_id TEXT NOT NULL,
  case_number TEXT NOT NULL,
  amount_xof INTEGER NOT NULL DEFAULT 3600,
  amount_usd_cents INTEGER NOT NULL DEFAULT 599,
  currency TEXT NOT NULL DEFAULT 'XOF',
  provider TEXT NOT NULL DEFAULT 'brav_pay',
  status TEXT NOT NULL DEFAULT 'pending',
  receipt_number TEXT NOT NULL DEFAULT '',
  verified_at_ms INTEGER NOT NULL DEFAULT 0,
  created_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cert_payments_user ON certification_payments(user_id, created_at_ms DESC);

CREATE TABLE IF NOT EXISTS notifications (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  sender_uid TEXT NOT NULL DEFAULT '',
  sender_name TEXT NOT NULL DEFAULT '',
  sender_avatar TEXT NOT NULL DEFAULT '',
  category TEXT NOT NULL DEFAULT 'messages',
  type TEXT NOT NULL DEFAULT 'message',
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  ref_id TEXT NOT NULL DEFAULT '',
  ref_type TEXT NOT NULL DEFAULT '',
  sub_ref_id TEXT NOT NULL DEFAULT '',
  dedup_key TEXT NOT NULL DEFAULT '',
  priority TEXT NOT NULL DEFAULT 'normal',
  is_read INTEGER NOT NULL DEFAULT 0,
  read_at_ms INTEGER NOT NULL DEFAULT 0,
  created_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications(user_id, created_at_ms DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_dedup ON notifications(user_id, dedup_key);

CREATE TABLE IF NOT EXISTS user_profile_flags (
  user_id TEXT PRIMARY KEY,
  is_private_profile INTEGER NOT NULL DEFAULT 0,
  allow_phone_lookup INTEGER NOT NULL DEFAULT 1,
  default_country_iso TEXT NOT NULL DEFAULT 'CI',
  updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS follow_requests (
  id TEXT PRIMARY KEY,
  from_uid TEXT NOT NULL,
  to_uid TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at_ms INTEGER NOT NULL,
  updated_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_follow_req_to ON follow_requests(to_uid, status);
CREATE INDEX IF NOT EXISTS idx_follow_req_from ON follow_requests(from_uid, status);

CREATE TABLE IF NOT EXISTS invite_links (
  code TEXT PRIMARY KEY,
  inviter_uid TEXT NOT NULL,
  inviter_display_name TEXT NOT NULL DEFAULT '',
  channel TEXT NOT NULL DEFAULT 'share',
  clicks INTEGER NOT NULL DEFAULT 0,
  signups INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1,
  created_at_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_invites_owner ON invite_links(inviter_uid, created_at_ms DESC);

CREATE TABLE IF NOT EXISTS caller_directory (
  phone_lookup_hash TEXT PRIMARY KEY,
  business_name TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'business',
  city TEXT NOT NULL DEFAULT '',
  country_iso TEXT NOT NULL DEFAULT 'CI',
  verified INTEGER NOT NULL DEFAULT 1,
  updated_at_ms INTEGER NOT NULL
);
