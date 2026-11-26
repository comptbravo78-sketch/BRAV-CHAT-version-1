/**
 * ============================================================================
 *  BRAV CHAT — Cloudflare Worker "Realtime Gateway"  v3.0.0
 *  URL publique : https://bold-darkness-7957.kolopay-ci.workers.dev
 * ============================================================================
 *  Firebase reste la source de vérité (comptes, conversations, messages, appels).
 *  Ce Worker ajoute tout le temps réel autour :
 *
 *   • Authentification Firebase vérifiée localement (JWT RS256, sans appel réseau)
 *   • Hub temps réel par utilisateur (WebSocket) : messages instantanés, "en train
 *     d'écrire", accusés de réception/lecture, présence en ligne, appel entrant
 *   • Signalisation WebRTC audio/vidéo (1 salle isolée par appel) avec file
 *     d'attente (le destinataire reçoit l'offre même s'il se connecte après)
 *   • TURN/STUN Cloudflare Realtime (identifiants temporaires) + proxy SFU optionnel
 *   • Notifications push FCM (messages, appels, appels manqués)
 *   • Détection d'appel manqué côté serveur
 *   • Médias de chat (photos, messages vocaux, vidéos, PDF) sur R2, avec Range
 *   • Recherche instantanée (utilisateurs + annonces)
 *   • Paiement GeniusPay : uniquement pour publier une annonce (+ webhook signé)
 *
 *  ----------------------------------------------------------------------------
 *  CONFIGURATION CLOUDFLARE
 *  ----------------------------------------------------------------------------
 *  Durable Objects (OBLIGATOIRE) :
 *     CALL_ROOMS -> classe CallRoom
 *     USER_HUBS  -> classe UserHub
 *
 *  Variables / Secrets (tous optionnels sauf mention) :
 *     FIREBASE_PROJECT_ID            = gen-lang-client-0455455029   (défaut intégré)
 *     FIRESTORE_DATABASE_ID          = ai-studio-android-bravchat-9b0fe8b4-691d-4a0a-87d0-1b835112167f (défaut intégré)
 *     TURN_KEY_ID           (var)    = ID de la clé TURN  (Cloudflare > Realtime > TURN)
 *     TURN_KEY_API_TOKEN    (secret) = token de cette clé TURN
 *     FIREBASE_SERVICE_ACCOUNT_JSON (secret) = JSON complet du compte de service Firebase
 *                                      -> active : push FCM, présence fiable, appels manqués,
 *                                         finalisation du paiement d'une annonce
 *     MEDIA                 (R2)     = bucket R2 pour photos/vocaux/vidéos
 *     CF_REALTIME_APP_ID / CF_REALTIME_APP_SECRET = SFU Cloudflare Realtime (appels de groupe)
 *     GENIUSPAY_API_KEY / GENIUSPAY_API_SECRET / GENIUSPAY_WEBHOOK_SECRET = paiement des annonces
 *     LISTING_PRICE_XOF              = prix de publication d'une annonce (défaut 500)
 *     ALLOWED_ORIGINS                = origines web autorisées, séparées par des virgules
 *     STUN_URLS / TURN_URLS / TURN_SHARED_SECRET = repli Coturn si pas de TURN Cloudflare
 *     MAX_UPLOAD_MB (défaut 25) · PRESENCE_SYNC=off · OFFER_HIDDEN_STATUSES · OFFER_ACTIVE_STATUS
 *     GENIUSPAY_API_BASE (défaut https://geniuspay.ci/api/v1/merchant)
 *     FIREBASE_WEB_API_KEY n'est PLUS nécessaire.
 *
 *  ----------------------------------------------------------------------------
 *  ROUTES  (Authorization: Bearer <Firebase ID token>)
 *  ----------------------------------------------------------------------------
 *   GET    /                         infos service
 *   GET    /health                   état + fonctionnalités actives (public)
 *   GET    /v1/config                configuration client (public)
 *   GET    /v1/whoami                vérifie un token
 *   GET    /v1/realtime              WebSocket hub utilisateur   (?deviceId=xxx)
 *   GET    /v1/calls/:id/signal      WebSocket signalisation WebRTC
 *   POST   /v1/calls/:id/ring        sonnerie chez le destinataire + minuterie d'appel manqué
 *   GET    /v1/turn-credentials      ICE servers temporaires
 *   ANY    /v1/sfu/:callId/...       proxy Cloudflare Realtime SFU (optionnel)
 *   POST   /v1/chat/notify           {conversationId, messageId} -> push temps réel + FCM
 *   POST   /v1/devices               {token, platform}  enregistre un jeton FCM
 *   DELETE /v1/devices               {token}
 *   GET    /v1/presence?uids=a,b,c   présence en ligne
 *   POST   /v1/media?conversationId= envoi binaire (image/audio/vidéo/pdf) -> R2
 *   GET    /v1/media/c/:cid/:name    lecture (Range supporté)
 *   DELETE /v1/media/c/:cid/:name
 *   GET    /v1/search?q=&type=users|offers|all&city=&category=&limit=
 *   POST   /v1/payments/listing      {offerId} -> lien de paiement GeniusPay
 *   GET    /v1/payments/:reference   statut (?refresh=1 interroge GeniusPay)
 *   POST   /webhooks/geniuspay       webhook GeniusPay (signature HMAC vérifiée)
 *
 *  WebSocket : le jeton peut être envoyé par l'en-tête Authorization (Android/OkHttp)
 *  ou, pour un navigateur, par Sec-WebSocket-Protocol: "brav.v1, bearer.<jeton>".
 * ============================================================================
 */

const VERSION = "3.0.0";
const PUBLIC_ORIGIN = "https://bold-darkness-7957.kolopay-ci.workers.dev";
const DEFAULT_PROJECT_ID = "gen-lang-client-0455455029";
const DEFAULT_DATABASE_ID = "ai-studio-android-bravchat-9b0fe8b4-691d-4a0a-87d0-1b835112167f";
const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

const WS_PROTOCOL = "brav.v1";
const MAX_SIGNAL_BYTES = 32 * 1024;
const MAX_HUB_MESSAGE_BYTES = 8 * 1024;
const MAX_JSON_BODY = 32 * 1024;
const RING_TIMEOUT_MS = 45 * 1000;
const MAX_CALL_MS = 3 * 60 * 60 * 1000;
const QUEUE_TTL_MS = 2 * 60 * 1000;
const QUEUE_MAX_ITEMS = 80;
const OFFLINE_GRACE_MS = 12 * 1000;
const MAX_SOCKETS_PER_USER = 5;
const MAX_WATCHERS = 500;
const MAX_DEVICES = 5;
const WS_OPEN = 1;

const ID_RE = /^[A-Za-z0-9_-]{1,128}$/;
const SIGNAL_TYPES = new Set(["offer", "answer", "ice-candidate", "call-state", "hangup", "ping"]);
const QUEUEABLE_TYPES = new Set(["offer", "answer", "ice-candidate", "call-state", "hangup"]);
const CALL_STATES = new Set(["ringing", "connecting", "connected", "reconnecting", "muted", "unmuted", "video-on", "video-off", "hold", "resume", "ended"]);
const MEDIA_TYPES = {
  "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp", "image/gif": "gif",
  "audio/mpeg": "mp3", "audio/mp4": "m4a", "audio/aac": "aac", "audio/ogg": "ogg",
  "audio/webm": "weba", "audio/wav": "wav", "audio/x-wav": "wav", "audio/3gpp": "3gp", "audio/amr": "amr",
  "video/mp4": "mp4", "video/webm": "webm", "video/3gpp": "3gp", "video/quicktime": "mov",
  "application/pdf": "pdf"
};
const INLINE_MEDIA = /^(image|audio|video)\//;

/* ============================================================================
 *  UTILITAIRES
 * ========================================================================== */

class HttpError extends Error {
  constructor(status, code, extra) {
    super(code);
    this.status = status;
    this.code = code;
    this.extra = extra || null;
  }
}

const fail = (status, error) => ({ ok: false, status, error });
const enc = new TextEncoder();
const dec = new TextDecoder();
const utf8 = (bytes) => dec.decode(bytes);
const splitCsv = (v) => String(v || "").split(",").map((s) => s.trim()).filter(Boolean);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function log(level, msg, data) {
  try { console.log(JSON.stringify({ level, msg, ...(data || {}) })); } catch { /* ignore */ }
}

function b64urlDecode(str) {
  let s = String(str).replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToB64(bytes) {
  let bin = "";
  for (const b of new Uint8Array(bytes)) bin += String.fromCharCode(b);
  return btoa(bin);
}

const b64urlEncode = (bytes) => bytesToB64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

function bytesToHex(bytes) {
  let out = "";
  for (const b of new Uint8Array(bytes)) out += b.toString(16).padStart(2, "0");
  return out;
}

function randomHex(n) {
  const a = new Uint8Array(n);
  crypto.getRandomValues(a);
  return bytesToHex(a);
}

function safeEqual(a, b) {
  a = String(a); b = String(b);
  let diff = a.length ^ b.length;
  const len = Math.max(a.length, b.length);
  for (let i = 0; i < len; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

async function hmac(hash, secret, data) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash }, false, ["sign"]);
  return crypto.subtle.sign("HMAC", key, enc.encode(data));
}

function json(data, status = 200, cors = new Headers(), extra = {}) {
  const headers = new Headers(cors);
  headers.set("Content-Type", "application/json; charset=utf-8");
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("Cache-Control", "no-store");
  for (const [k, v] of Object.entries(extra)) headers.set(k, v);
  return new Response(JSON.stringify(data), { status, headers });
}

function corsHeaders(request, env) {
  const origin = request.headers.get("Origin");
  const allowed = splitCsv(env.ALLOWED_ORIGINS);
  const h = new Headers({
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, Range, X-Request-Id",
    "Access-Control-Expose-Headers": "Content-Range, Accept-Ranges, Content-Length, ETag, X-Request-Id",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin"
  });
  if (origin && (allowed.includes("*") || allowed.includes(origin))) {
    h.set("Access-Control-Allow-Origin", allowed.includes("*") ? "*" : origin);
  }
  return h;
}

async function readJson(request, maxBytes = MAX_JSON_BODY) {
  const declared = Number(request.headers.get("Content-Length") || 0);
  if (declared > maxBytes) throw new HttpError(413, "BODY_TOO_LARGE");
  const text = await request.text();
  if (enc.encode(text).length > maxBytes) throw new HttpError(413, "BODY_TOO_LARGE");
  if (!text) return {};
  try { return JSON.parse(text); } catch { throw new HttpError(400, "INVALID_JSON"); }
}

const rlState = new Map();
function rateLimit(key, limit, windowMs) {
  const now = Date.now();
  let e = rlState.get(key);
  if (!e || now - e.start >= windowMs) { e = { start: now, count: 0 }; rlState.set(key, e); }
  e.count++;
  if (rlState.size > 5000) {
    for (const [k, v] of rlState) if (now - v.start >= 60000) rlState.delete(k);
  }
  return e.count <= limit;
}

function enforceRate(uid, bucket, limit, windowMs) {
  if (!rateLimit(`${bucket}:${uid}`, limit, windowMs)) throw new HttpError(429, "RATE_LIMITED");
}

const memCache = new Map();
function cacheGet(key) {
  const e = memCache.get(key);
  if (!e) return undefined;
  if (Date.now() > e.exp) { memCache.delete(key); return undefined; }
  return e.value;
}
function cacheSet(key, value, ttlMs) {
  if (memCache.size > 3000) {
    const now = Date.now();
    for (const [k, v] of memCache) if (now > v.exp) memCache.delete(k);
  }
  memCache.set(key, { value, exp: Date.now() + ttlMs });
}

function isValidId(v) { return typeof v === "string" && ID_RE.test(v); }
function convIdFor(a, b) { const s = [a, b].sort(); return `conv_${s[0]}_${s[1]}`; }
function hubStub(env, uid) { return env.USER_HUBS.get(env.USER_HUBS.idFromName(`u:${uid}`)); }

async function hubCall(env, uid, path, body, method = "POST") {
  const res = await hubStub(env, uid).fetch(`https://user-hub.internal${path}`, {
    method,
    headers: { "Content-Type": "application/json", "X-Brav-Hub-Uid": uid },
    body: method === "GET" ? undefined : JSON.stringify(body || {})
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* ignore */ }
  return { ok: res.ok, status: res.status, data };
}

/* ============================================================================
 *  AUTHENTIFICATION FIREBASE (vérification locale du JWT)
 * ========================================================================== */

const jwkState = { keys: new Map(), fetchedAt: 0, expiresAt: 0, inflight: null };

function parseMaxAge(cc) {
  const m = /max-age=(\d+)/i.exec(cc || "");
  return m ? Number(m[1]) : null;
}

async function loadJwks(force) {
  const now = Date.now();
  if (!force && jwkState.keys.size && now < jwkState.expiresAt) return;
  if (force && now - jwkState.fetchedAt < 30000) return;
  if (jwkState.inflight) return jwkState.inflight;
  jwkState.inflight = (async () => {
    try {
      const res = await fetch(JWKS_URL, { cf: { cacheTtl: 3600, cacheEverything: true } });
      if (!res.ok) throw new Error(`jwks ${res.status}`);
      const body = await res.json();
      const map = new Map();
      for (const jwk of body.keys || []) {
        if (jwk && jwk.kid && jwk.kty === "RSA") map.set(jwk.kid, { jwk, key: null });
      }
      if (!map.size) throw new Error("jwks empty");
      const maxAge = parseMaxAge(res.headers.get("cache-control")) || 3600;
      jwkState.keys = map;
      jwkState.fetchedAt = Date.now();
      jwkState.expiresAt = Date.now() + Math.min(Math.max(maxAge, 300), 21600) * 1000;
    } finally {
      jwkState.inflight = null;
    }
  })();
  return jwkState.inflight;
}

async function verifyFirebaseIdToken(token, env) {
  const projectId = env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT_ID;
  const parts = String(token).split(".");
  if (parts.length !== 3 || token.length > 4096) return fail(401, "INVALID_FIREBASE_ID_TOKEN");

  let header, payload;
  try {
    header = JSON.parse(utf8(b64urlDecode(parts[0])));
    payload = JSON.parse(utf8(b64urlDecode(parts[1])));
  } catch { return fail(401, "INVALID_FIREBASE_ID_TOKEN"); }

  if (header.alg !== "RS256" || !header.kid) return fail(401, "INVALID_FIREBASE_ID_TOKEN");
  if (payload.aud !== projectId || payload.iss !== `https://securetoken.google.com/${projectId}`) {
    return fail(401, "WRONG_FIREBASE_PROJECT");
  }
  if (typeof payload.sub !== "string" || !payload.sub || payload.sub.length > 128) return fail(401, "INVALID_FIREBASE_ID_TOKEN");

  const now = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== "number" || payload.exp < now - 30) return fail(401, "FIREBASE_ID_TOKEN_EXPIRED");
  if (typeof payload.iat === "number" && payload.iat > now + 300) return fail(401, "INVALID_FIREBASE_ID_TOKEN");
  if (typeof payload.auth_time === "number" && payload.auth_time > now + 300) return fail(401, "INVALID_FIREBASE_ID_TOKEN");

  try { await loadJwks(false); } catch (e) {
    if (!jwkState.keys.size) return fail(503, "AUTH_KEYS_UNAVAILABLE");
  }
  let entry = jwkState.keys.get(header.kid);
  if (!entry) {
    try { await loadJwks(true); } catch { /* ignore */ }
    entry = jwkState.keys.get(header.kid);
  }
  if (!entry) return fail(401, "UNKNOWN_SIGNING_KEY");

  try {
    if (!entry.key) {
      entry.key = await crypto.subtle.importKey(
        "jwk", entry.jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
      );
    }
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5", entry.key, b64urlDecode(parts[2]), enc.encode(`${parts[0]}.${parts[1]}`)
    );
    if (!valid) return fail(401, "INVALID_FIREBASE_ID_TOKEN");
  } catch { return fail(401, "INVALID_FIREBASE_ID_TOKEN"); }

  return { ok: true, uid: payload.sub, claims: payload };
}

function extractToken(request) {
  const header = request.headers.get("Authorization") || "";
  const m = header.match(/^Bearer\s+(.+)$/i);
  if (m) return m[1].trim();
  const protos = splitCsv(request.headers.get("Sec-WebSocket-Protocol"));
  for (const p of protos) if (p.startsWith("bearer.")) return p.slice(7);
  return null;
}

async function authenticate(request, env) {
  const token = extractToken(request);
  if (!token) return fail(401, "MISSING_FIREBASE_ID_TOKEN");
  const v = await verifyFirebaseIdToken(token, env);
  if (!v.ok) return v;
  return { ok: true, uid: v.uid, claims: v.claims, token };
}

async function requireAuth(request, env) {
  const a = await authenticate(request, env);
  if (!a.ok) throw new HttpError(a.status, a.error);
  return a;
}

/* ============================================================================
 *  FIRESTORE (REST) — avec le jeton de l'utilisateur (les règles s'appliquent)
 * ========================================================================== */

function fsRoot(env) {
  const p = env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT_ID;
  const d = env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID;
  return `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(p)}/databases/${encodeURIComponent(d)}/documents`;
}

const fsPathEnc = (path) => path.split("/").map(encodeURIComponent).join("/");

function decodeValue(v) {
  if (!v || typeof v !== "object") return null;
  if ("stringValue" in v) return v.stringValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return Number(v.doubleValue);
  if ("booleanValue" in v) return v.booleanValue;
  if ("timestampValue" in v) return v.timestampValue;
  if ("nullValue" in v) return null;
  if ("referenceValue" in v) return v.referenceValue;
  if ("geoPointValue" in v) return v.geoPointValue;
  if ("arrayValue" in v) return (v.arrayValue.values || []).map(decodeValue);
  if ("mapValue" in v) return decodeFields(v.mapValue.fields || {});
  return null;
}

function decodeFields(fields) {
  const out = {};
  for (const [k, v] of Object.entries(fields || {})) out[k] = decodeValue(v);
  return out;
}

function encodeValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (typeof v === "string") return { stringValue: v };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(encodeValue) } };
  if (typeof v === "object") {
    const fields = {};
    for (const [k, x] of Object.entries(v)) fields[k] = encodeValue(x);
    return { mapValue: { fields } };
  }
  return { stringValue: String(v) };
}

async function fsGetDoc(env, path, bearer) {
  try {
    const res = await fetch(`${fsRoot(env)}/${fsPathEnc(path)}`, { headers: { Authorization: `Bearer ${bearer}` } });
    if (res.status === 404) return fail(404, "NOT_FOUND");
    if (res.status === 401 || res.status === 403) return fail(403, "ACCESS_DENIED");
    if (!res.ok) return fail(502, "FIRESTORE_LOOKUP_FAILED");
    const doc = await res.json();
    return { ok: true, data: decodeFields(doc.fields || {}), updateTime: doc.updateTime, name: doc.name };
  } catch { return fail(503, "FIRESTORE_UNAVAILABLE"); }
}

async function fsRunQuery(env, structuredQuery, bearer) {
  try {
    const res = await fetch(`${fsRoot(env)}:runQuery`, {
      method: "POST",
      headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({ structuredQuery })
    });
    if (res.status === 401 || res.status === 403) return fail(403, "ACCESS_DENIED");
    if (!res.ok) return fail(502, "FIRESTORE_QUERY_FAILED");
    const rows = await res.json();
    const docs = [];
    for (const r of rows || []) {
      if (!r.document) continue;
      const id = r.document.name.split("/").pop();
      docs.push({ id, ...decodeFields(r.document.fields || {}) });
    }
    return { ok: true, docs };
  } catch { return fail(503, "FIRESTORE_UNAVAILABLE"); }
}

/* ---- accès administrateur (compte de service) : FCM, présence, paiements ---- */

const saState = { raw: null, parsed: null, token: null, tokenExp: 0, keyObj: null, inflight: null };

function getServiceAccount(env) {
  const raw = env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;
  if (saState.raw === raw) return saState.parsed;
  try {
    const sa = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!sa.client_email || !sa.private_key) throw new Error("incomplete");
    saState.raw = raw; saState.parsed = sa; saState.token = null; saState.tokenExp = 0; saState.keyObj = null;
    return sa;
  } catch {
    log("error", "FIREBASE_SERVICE_ACCOUNT_JSON invalide");
    saState.raw = raw; saState.parsed = null;
    return null;
  }
}

async function getAdminToken(env) {
  const sa = getServiceAccount(env);
  if (!sa) return null;
  const now = Math.floor(Date.now() / 1000);
  if (saState.token && saState.tokenExp - 300 > now) return saState.token;
  if (saState.inflight) return saState.inflight;
  saState.inflight = (async () => {
    try {
      if (!saState.keyObj) {
        const pem = String(sa.private_key).replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\n|\s/g, "");
        saState.keyObj = await crypto.subtle.importKey(
          "pkcs8", b64urlDecode(pem.replace(/\+/g, "-").replace(/\//g, "_")), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
        );
      }
      const head = b64urlEncode(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
      const claims = b64urlEncode(enc.encode(JSON.stringify({
        iss: sa.client_email,
        scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600
      })));
      const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", saState.keyObj, enc.encode(`${head}.${claims}`));
      const res = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${head}.${claims}.${b64urlEncode(sig)}`
      });
      if (!res.ok) throw new Error(`oauth ${res.status}`);
      const body = await res.json();
      saState.token = body.access_token;
      saState.tokenExp = now + (Number(body.expires_in) || 3600);
      return saState.token;
    } catch (e) {
      log("error", "admin token failure", { message: String(e && e.message) });
      saState.token = null;
      return null;
    } finally {
      saState.inflight = null;
    }
  })();
  return saState.inflight;
}

async function adminGetDoc(env, path) {
  const token = await getAdminToken(env);
  if (!token) return fail(503, "NO_SERVICE_ACCOUNT");
  return fsGetDoc(env, path, token);
}

async function adminPatchDoc(env, path, fields, opts = {}) {
  const token = await getAdminToken(env);
  if (!token) return fail(503, "NO_SERVICE_ACCOUNT");
  const q = new URLSearchParams();
  const encoded = {};
  for (const [k, v] of Object.entries(fields)) { encoded[k] = encodeValue(v); }
  const url = new URL(`${fsRoot(env)}/${fsPathEnc(path)}`);
  for (const k of Object.keys(fields)) url.searchParams.append("updateMask.fieldPaths", k);
  if (opts.updateTime) url.searchParams.set("currentDocument.updateTime", opts.updateTime);
  else url.searchParams.set("currentDocument.exists", "true");
  void q;
  try {
    const res = await fetch(url.toString(), {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ fields: encoded })
    });
    if (!res.ok) return fail(res.status === 404 || res.status === 400 || res.status === 409 ? 409 : 502, "PATCH_FAILED");
    return { ok: true };
  } catch { return fail(503, "FIRESTORE_UNAVAILABLE"); }
}

/* ---- FCM v1 ---- */

async function sendFcm(env, deviceToken, { title, body, data, ttl }) {
  const sa = getServiceAccount(env);
  if (!sa) return { ok: false, error: "NO_SERVICE_ACCOUNT" };
  const at = await getAdminToken(env);
  if (!at) return { ok: false, error: "NO_ADMIN_TOKEN" };
  const projectId = env.FCM_PROJECT_ID || sa.project_id || env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT_ID;
  const strData = {};
  for (const [k, v] of Object.entries(data || {})) strData[k] = String(v ?? "");
  const message = { token: deviceToken, data: strData, android: { priority: "HIGH", ttl: `${ttl || 60}s` } };
  if (title || body) message.notification = { title: String(title || ""), body: String(body || "") };
  try {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${at}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message })
    });
    if (res.ok) return { ok: true };
    const text = await res.text();
    const invalid = res.status === 404 || /UNREGISTERED|registration token is not a valid/i.test(text);
    return { ok: false, invalid, error: `FCM_${res.status}` };
  } catch { return { ok: false, error: "FCM_UNREACHABLE" }; }
}

/* ============================================================================
 *  POINT D'ENTRÉE
 * ========================================================================== */

export default {
  async fetch(request, env, ctx) {
    const requestId = request.headers.get("X-Request-Id") || crypto.randomUUID();
    const cors = corsHeaders(request, env);
    cors.set("X-Request-Id", requestId);
    try {
      if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
      return await route(request, env, ctx, cors);
    } catch (err) {
      if (err instanceof HttpError) {
        return json({ error: err.code, requestId, ...(err.extra || {}) }, err.status, cors);
      }
      log("error", "unhandled", { requestId, message: String(err && err.message), stack: String(err && err.stack).slice(0, 600) });
      return json({ error: "INTERNAL_ERROR", requestId }, 500, cors);
    }
  }
};

async function route(request, env, ctx, cors) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method;

  if (path === "/" && method === "GET") {
    return json({ service: "BRAV CHAT Realtime Gateway", version: VERSION, status: "online", health: `${PUBLIC_ORIGIN}/health` }, 200, cors);
  }
  if (path === "/health" && method === "GET") return health(request, env, cors);
  if (path === "/v1/config" && method === "GET") return json(clientConfig(env), 200, cors, { "Cache-Control": "public, max-age=300" });
  if (path === "/pay/success" || path === "/pay/error") return payReturnPage(path === "/pay/success");
  if (path === "/webhooks/geniuspay" && method === "POST") return geniusPayWebhook(request, env, cors);

  if (path === "/v1/whoami" && method === "GET") {
    const a = await requireAuth(request, env);
    return json({ uid: a.uid, email: a.claims.email || null, name: a.claims.name || null }, 200, cors);
  }

  if (path === "/v1/realtime" && method === "GET") return openRealtime(request, env, url);

  if (path === "/v1/turn-credentials" && method === "GET") {
    const a = await requireAuth(request, env);
    enforceRate(a.uid, "turn", 30, 60000);
    const ttl = Math.min(Math.max(Number(url.searchParams.get("ttl")) || 14400, 600), 86400);
    const result = await turnCredentials(env, a.uid, ttl);
    return json(result, 200, cors);
  }

  const signal = path.match(/^\/v1\/calls\/([A-Za-z0-9_-]{1,128})\/signal$/);
  if (signal && method === "GET") return openCallSignal(request, env, signal[1]);

  const ring = path.match(/^\/v1\/calls\/([A-Za-z0-9_-]{1,128})\/ring$/);
  if (ring && method === "POST") return ringCall(request, env, ring[1], cors);

  const sfu = path.match(/^\/v1\/sfu\/([A-Za-z0-9_-]{1,128})\/(.+)$/);
  if (sfu) return sfuProxy(request, env, sfu[1], sfu[2], cors);

  if (path === "/v1/chat/notify" && method === "POST") return chatNotify(request, env, cors);

  if (path === "/v1/devices" && (method === "POST" || method === "DELETE")) return manageDevice(request, env, cors, method);

  if (path === "/v1/presence" && method === "GET") return presenceQuery(request, env, url, cors);

  if (path === "/v1/media" && method === "POST") return mediaUpload(request, env, url, cors);
  const media = path.match(/^\/v1\/media\/c\/([A-Za-z0-9_-]{1,128})\/([A-Za-z0-9._-]{1,128})$/);
  if (media && (method === "GET" || method === "HEAD")) return mediaDownload(request, env, media[1], media[2], cors);
  if (media && method === "DELETE") return mediaDelete(request, env, media[1], media[2], cors);

  if (path === "/v1/search" && method === "GET") return search(request, env, url, cors);

  if (path === "/v1/payments/listing" && method === "POST") return createListingPayment(request, env, cors);
  const pay = path.match(/^\/v1\/payments\/([A-Za-z0-9_-]{1,64})$/);
  if (pay && method === "GET") return getPayment(request, env, url, pay[1], cors);

  return json({ error: "NOT_FOUND", service: "BRAV CHAT Realtime Gateway" }, 404, cors);
}

/* ============================================================================
 *  SANTÉ & CONFIG
 * ========================================================================== */

function paymentsConfigured(env) {
  return Boolean(env.GENIUSPAY_API_KEY && env.GENIUSPAY_API_SECRET);
}

function features(env) {
  const sa = Boolean(getServiceAccount(env));
  return {
    auth: true,
    realtimeHub: Boolean(env.USER_HUBS),
    callSignaling: Boolean(env.CALL_ROOMS),
    turn: env.TURN_KEY_ID && env.TURN_KEY_API_TOKEN ? "cloudflare" : (splitCsv(env.TURN_URLS).length && env.TURN_SHARED_SECRET ? "coturn" : false),
    sfu: Boolean(env.CF_REALTIME_APP_ID && env.CF_REALTIME_APP_SECRET),
    media: Boolean(env.MEDIA),
    push: sa,
    presenceSync: sa && env.PRESENCE_SYNC !== "off",
    missedCalls: sa && Boolean(env.CALL_ROOMS),
    search: true,
    payments: paymentsConfigured(env),
    paymentFinalization: paymentsConfigured(env) && sa
  };
}

function health(request, env, cors) {
  const host = new URL(request.url).host;
  return json({
    service: "BRAV CHAT Realtime Gateway",
    version: VERSION,
    status: "online",
    publicUrl: PUBLIC_ORIGIN,
    host,
    hostMatchesPublicUrl: host === new URL(PUBLIC_ORIGIN).host,
    time: new Date().toISOString(),
    firebaseProject: env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT_ID,
    firestoreDatabase: env.FIRESTORE_DATABASE_ID || DEFAULT_DATABASE_ID,
    features: features(env),
    mediaTransport: "WebRTC (P2P + relais TURN Cloudflare) ; le Worker ne transporte que la signalisation"
  }, 200, cors);
}

function clientConfig(env) {
  const wsBase = PUBLIC_ORIGIN.replace(/^http/, "ws");
  return {
    version: VERSION,
    baseUrl: PUBLIC_ORIGIN,
    realtimeUrl: `${wsBase}/v1/realtime`,
    callSignalUrlTemplate: `${wsBase}/v1/calls/{callId}/signal`,
    websocketSubprotocol: WS_PROTOCOL,
    heartbeat: { clientSends: "ping", serverReplies: "pong", everyMs: 25000 },
    limits: {
      signalBytes: MAX_SIGNAL_BYTES,
      uploadMB: uploadLimitMB(env),
      presenceUidsPerQuery: 100,
      ringTimeoutMs: RING_TIMEOUT_MS
    },
    mediaTypes: Object.keys(MEDIA_TYPES),
    features: features(env)
  };
}

/* ============================================================================
 *  WEBSOCKETS : hub utilisateur + signalisation d'appel
 * ========================================================================== */

function upgradeResponse(res, request) {
  if (res.status !== 101) return res;
  const offered = splitCsv(request.headers.get("Sec-WebSocket-Protocol"));
  if (offered.includes(WS_PROTOCOL)) {
    return new Response(null, { status: 101, webSocket: res.webSocket, headers: { "Sec-WebSocket-Protocol": WS_PROTOCOL } });
  }
  return res;
}

async function openRealtime(request, env, url) {
  if ((request.headers.get("Upgrade") || "").toLowerCase() !== "websocket") throw new HttpError(426, "WEBSOCKET_REQUIRED");
  if (!env.USER_HUBS) throw new HttpError(503, "USER_HUBS_BINDING_MISSING");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "ws-connect", 40, 60000);
  const deviceId = (url.searchParams.get("deviceId") || "").replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64) || randomHex(6);
  const headers = new Headers({
    "Upgrade": "websocket",
    "X-Brav-Uid": a.uid,
    "X-Brav-Hub-Uid": a.uid,
    "X-Brav-Device": deviceId
  });
  const res = await hubStub(env, a.uid).fetch(new Request("https://user-hub.internal/ws", { method: "GET", headers }));
  return upgradeResponse(res, request);
}

async function loadCall(env, auth, callId) {
  const cacheKey = `call:${auth.uid}:${callId}`;
  const call = await fsGetDoc(env, `calls/${callId}`, auth.token);
  if (!call.ok) return call;
  const d = call.data;
  if (!d.callerId || !d.receiverId) return fail(422, "INVALID_CALL_RECORD");
  if (auth.uid !== d.callerId && auth.uid !== d.receiverId) return fail(403, "NOT_A_CALL_PARTICIPANT");
  void cacheKey;
  return {
    ok: true,
    callerId: d.callerId, receiverId: d.receiverId, status: d.status || "calling",
    callType: d.callType === "video" ? "video" : "audio",
    callerName: d.callerName || "", receiverName: d.receiverName || ""
  };
}

async function openCallSignal(request, env, callId) {
  if ((request.headers.get("Upgrade") || "").toLowerCase() !== "websocket") throw new HttpError(426, "WEBSOCKET_REQUIRED");
  if (!env.CALL_ROOMS) throw new HttpError(503, "CALL_ROOMS_BINDING_MISSING");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "signal-connect", 60, 60000);
  const call = await loadCall(env, a, callId);
  if (!call.ok) throw new HttpError(call.status, call.error);
  if (!["calling", "accepted"].includes(call.status)) throw new HttpError(409, "CALL_NOT_ACTIVE");

  const room = env.CALL_ROOMS.get(env.CALL_ROOMS.idFromName(callId));
  const headers = new Headers({
    "Upgrade": "websocket",
    "X-Brav-Uid": a.uid,
    "X-Brav-Caller": call.callerId,
    "X-Brav-Receiver": call.receiverId,
    "X-Brav-Call-Id": callId,
    "X-Brav-Call-Type": call.callType
  });
  const res = await room.fetch(new Request("https://call-room.internal/connect", { method: "GET", headers }));
  return upgradeResponse(res, request);
}

async function ringCall(request, env, callId, cors) {
  if (!env.USER_HUBS) throw new HttpError(503, "USER_HUBS_BINDING_MISSING");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "ring", 20, 60000);
  const call = await loadCall(env, a, callId);
  if (!call.ok) throw new HttpError(call.status, call.error);
  if (a.uid !== call.callerId) throw new HttpError(403, "ONLY_CALLER_CAN_RING");
  if (call.status !== "calling") throw new HttpError(409, "CALL_NOT_RINGING");

  const event = {
    type: "incoming_call", callId, callType: call.callType,
    callerId: call.callerId, callerName: call.callerName || a.claims.name || "", sentAt: Date.now()
  };
  const push = await hubCall(env, call.receiverId, "/push", { event });
  const pushed = await hubCall(env, call.receiverId, "/fcm", {
    kind: "call",
    data: { kind: "incoming_call", callId, callType: call.callType, callerId: call.callerId, callerName: event.callerName },
    ttl: 45
  });

  if (env.CALL_ROOMS && getServiceAccount(env)) {
    const room = env.CALL_ROOMS.get(env.CALL_ROOMS.idFromName(callId));
    await room.fetch("https://call-room.internal/arm", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Brav-Caller": call.callerId, "X-Brav-Receiver": call.receiverId, "X-Brav-Call-Id": callId
      },
      body: JSON.stringify({ callerName: event.callerName, callType: call.callType, delayMs: RING_TIMEOUT_MS })
    }).catch(() => {});
  }
  return json({
    ok: true,
    delivered: { realtime: (push.data && push.data.delivered) || 0, push: (pushed.data && pushed.data.sent) || 0 },
    missedCallTimer: Boolean(env.CALL_ROOMS && getServiceAccount(env))
  }, 200, cors);
}

/* ---------------------------------------------------------------------------
 *  Durable Object : un hub temps réel par utilisateur
 * ------------------------------------------------------------------------- */
export class UserHub {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.uid = null;
    this.presence = { online: false, lastSeen: 0 };
    this.buckets = new WeakMap();
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
    ctx.blockConcurrencyWhile(async () => {
      this.uid = (await ctx.storage.get("uid")) || null;
      this.presence = (await ctx.storage.get("presence")) || this.presence;
    });
  }

  live() { return this.ctx.getWebSockets().filter((ws) => ws.readyState === WS_OPEN); }

  async remember(uid) {
    if (uid && this.uid !== uid) { this.uid = uid; await this.ctx.storage.put("uid", uid); }
  }

  async fetch(request) {
    const url = new URL(request.url);
    await this.remember(request.headers.get("X-Brav-Hub-Uid"));
    if (url.pathname === "/ws") return this.openSocket(request);
    const body = request.method === "GET" ? {} : await request.json().catch(() => ({}));
    switch (url.pathname) {
      case "/push": return this.reply(await this.push(body.event));
      case "/presence": return this.reply({ uid: this.uid, online: this.presence.online, lastSeen: this.presence.lastSeen });
      case "/watch": return this.reply(await this.addWatcher(body.watcher));
      case "/devices": return this.reply(await this.manageDevices(request.method, body));
      case "/fcm": return this.reply(await this.fcm(body));
      case "/pay/create": return this.reply(await this.payCreate(body));
      case "/pay/find": return this.reply(await this.payFind(body));
      case "/pay/get": return this.reply((await this.ctx.storage.get(`pay:${body.reference}`)) || null);
      case "/pay/event": return this.reply(await this.payEvent(body));
      default: return new Response("Not found", { status: 404 });
    }
  }

  reply(data) { return new Response(JSON.stringify(data === undefined ? null : data), { headers: { "Content-Type": "application/json" } }); }

  async openSocket(request) {
    if ((request.headers.get("Upgrade") || "").toLowerCase() !== "websocket") return new Response("WebSocket required", { status: 426 });
    const uid = request.headers.get("X-Brav-Uid");
    if (!uid) return new Response("Forbidden", { status: 403 });
    const deviceId = request.headers.get("X-Brav-Device") || randomHex(6);

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    const previous = this.live();
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ uid, deviceId, connectedAt: Date.now() });

    // Remplace la même appareil, et plafonne le nombre de connexions.
    const sorted = previous.sort((a, b) => (a.deserializeAttachment()?.connectedAt || 0) - (b.deserializeAttachment()?.connectedAt || 0));
    for (const ws of sorted) {
      if (ws.deserializeAttachment()?.deviceId === deviceId) { this.closeQuietly(ws, 4000, "replaced"); }
    }
    const remaining = sorted.filter((ws) => ws.readyState === WS_OPEN && ws.deserializeAttachment()?.deviceId !== deviceId);
    while (remaining.length >= MAX_SOCKETS_PER_USER) this.closeQuietly(remaining.shift(), 4001, "too many connections");

    await this.setOnline();
    server.send(JSON.stringify({
      type: "ready", uid, deviceId, serverTime: Date.now(), connections: this.live().length, protocol: WS_PROTOCOL
    }));
    return new Response(null, { status: 101, webSocket: client });
  }

  closeQuietly(ws, code, reason) { try { ws.close(code, reason); } catch { /* ignore */ } }

  async setOnline() {
    await this.ctx.storage.deleteAlarm();
    if (!this.presence.online) {
      this.presence = { online: true, lastSeen: Date.now() };
      await this.ctx.storage.put("presence", this.presence);
      this.ctx.waitUntil(this.syncPresence(true));
    }
  }

  async alarm() {
    if (this.live().length === 0 && this.presence.online) {
      this.presence = { online: false, lastSeen: Date.now() };
      await this.ctx.storage.put("presence", this.presence);
      await this.syncPresence(false);
    }
  }

  async syncPresence(online) {
    try {
      if (this.uid && this.env.PRESENCE_SYNC !== "off" && getServiceAccount(this.env)) {
        await adminPatchDoc(this.env, `users/${this.uid}`, { isOnline: online, lastSeenAt: new Date() });
      }
      const watchers = (await this.ctx.storage.get("watchers")) || [];
      if (!watchers.length) return;
      const event = { type: "presence", uid: this.uid, online, lastSeen: this.presence.lastSeen };
      const keep = [];
      await Promise.all(watchers.map(async (w) => {
        try {
          const r = await hubCall(this.env, w, "/push", { event });
          if (r.data && r.data.delivered > 0) keep.push(w);
        } catch { keep.push(w); }
      }));
      if (keep.length !== watchers.length) await this.ctx.storage.put("watchers", keep);
    } catch (e) { log("warn", "syncPresence failed", { message: String(e && e.message) }); }
  }

  async addWatcher(watcher) {
    if (isValidId(watcher) && watcher !== this.uid) {
      const list = (await this.ctx.storage.get("watchers")) || [];
      if (!list.includes(watcher)) {
        list.push(watcher);
        while (list.length > MAX_WATCHERS) list.shift();
        await this.ctx.storage.put("watchers", list);
      }
    }
    return { uid: this.uid, online: this.presence.online, lastSeen: this.presence.lastSeen };
  }

  async push(event) {
    if (!event || typeof event !== "object") return { delivered: 0, sockets: 0 };
    const raw = JSON.stringify(event);
    let delivered = 0;
    const sockets = this.live();
    for (const ws of sockets) {
      try { ws.send(raw); delivered++; } catch { this.closeQuietly(ws, 1011, "delivery failed"); }
    }
    return { delivered, sockets: sockets.length };
  }

  async manageDevices(method, body) {
    const devices = (await this.ctx.storage.get("devices")) || [];
    const token = typeof body.token === "string" ? body.token : "";
    if (!token || token.length > 4096) return { ok: false, error: "INVALID_TOKEN", count: devices.length };
    let list = devices.filter((d) => d.token !== token);
    if (method !== "DELETE") {
      list.push({ token, platform: String(body.platform || "android").slice(0, 16), updatedAt: Date.now() });
      while (list.length > MAX_DEVICES) list.shift();
    }
    await this.ctx.storage.put("devices", list);
    return { ok: true, count: list.length };
  }

  async fcm({ kind, title, body, data, ttl }) {
    const devices = (await this.ctx.storage.get("devices")) || [];
    if (!devices.length) return { sent: 0, failed: 0, devices: 0 };
    let sent = 0, failed = 0;
    const valid = [];
    for (const d of devices) {
      const r = await sendFcm(this.env, d.token, { title, body, data: { ...(data || {}), kind: (data && data.kind) || kind }, ttl });
      if (r.ok) { sent++; valid.push(d); }
      else { failed++; if (!r.invalid) valid.push(d); }
    }
    if (valid.length !== devices.length) await this.ctx.storage.put("devices", valid);
    return { sent, failed, devices: valid.length };
  }

  /* ---- messages WebSocket du client ---- */
  async webSocketMessage(ws, raw) {
    const att = ws.deserializeAttachment();
    if (!att) return this.closeQuietly(ws, 1011, "missing session");
    const text = typeof raw === "string" ? raw : dec.decode(raw);
    if (enc.encode(text).length > MAX_HUB_MESSAGE_BYTES) return this.closeQuietly(ws, 1009, "message too large");

    let bucket = this.buckets.get(ws);
    const now = Date.now();
    if (!bucket) { bucket = { tokens: 40, last: now }; this.buckets.set(ws, bucket); }
    bucket.tokens = Math.min(40, bucket.tokens + ((now - bucket.last) / 1000) * 15);
    bucket.last = now;
    if (bucket.tokens < 1) return ws.send(JSON.stringify({ type: "error", error: "RATE_LIMITED" }));
    bucket.tokens -= 1;

    let msg;
    try { msg = JSON.parse(text); } catch { return ws.send(JSON.stringify({ type: "error", error: "INVALID_JSON" })); }
    if (!msg || typeof msg.type !== "string") return ws.send(JSON.stringify({ type: "error", error: "INVALID_MESSAGE" }));

    const uid = att.uid;
    switch (msg.type) {
      case "ping":
        return ws.send(JSON.stringify({ type: "pong", timestamp: Date.now() }));

      case "typing": {
        if (!isValidId(msg.to) || !isValidId(msg.conversationId) || msg.conversationId !== convIdFor(uid, msg.to)) {
          return ws.send(JSON.stringify({ type: "error", error: "INVALID_CONVERSATION" }));
        }
        await hubCall(this.env, msg.to, "/push", {
          event: { type: "typing", conversationId: msg.conversationId, from: uid, isTyping: msg.isTyping !== false, sentAt: Date.now() }
        });
        return;
      }

      case "receipt": {
        if (!isValidId(msg.to) || !isValidId(msg.conversationId) || !isValidId(msg.messageId) ||
            msg.conversationId !== convIdFor(uid, msg.to) || !["delivered", "read"].includes(msg.status)) {
          return ws.send(JSON.stringify({ type: "error", error: "INVALID_RECEIPT" }));
        }
        await hubCall(this.env, msg.to, "/push", {
          event: { type: "receipt", conversationId: msg.conversationId, messageId: msg.messageId, status: msg.status, from: uid, sentAt: Date.now() }
        });
        return;
      }

      case "watch": {
        const uids = Array.isArray(msg.uids) ? [...new Set(msg.uids.filter(isValidId))].filter((u) => u !== uid).slice(0, 100) : [];
        const list = await Promise.all(uids.map(async (u) => {
          try {
            const r = await hubCall(this.env, u, "/watch", { watcher: uid });
            return r.data || { uid: u, online: false, lastSeen: 0 };
          } catch { return { uid: u, online: false, lastSeen: 0 }; }
        }));
        return ws.send(JSON.stringify({ type: "presence_snapshot", users: list }));
      }

      default:
        return ws.send(JSON.stringify({ type: "error", error: "UNSUPPORTED_MESSAGE_TYPE" }));
    }
  }

  async webSocketClose(ws, code, reason) {
    this.closeQuietly(ws, code && ![1005, 1006, 1015].includes(code) ? code : 1000, reason || "closed");
    await this.scheduleOfflineIfEmpty();
  }

  async webSocketError(ws) {
    this.closeQuietly(ws, 1011, "websocket error");
    await this.scheduleOfflineIfEmpty();
  }

  async scheduleOfflineIfEmpty() {
    if (this.live().length === 0) await this.ctx.storage.setAlarm(Date.now() + OFFLINE_GRACE_MS);
  }

  /* ---- registre des paiements d'annonces ---- */
  async payCreate({ reference, offerId, amount, checkoutUrl }) {
    const entry = { reference, offerId, amount, checkoutUrl, status: "pending", createdAt: Date.now() };
    await this.ctx.storage.put(`pay:${reference}`, entry);
    await this.ctx.storage.put(`payoffer:${offerId}`, reference);
    return entry;
  }

  async payFind({ offerId }) {
    const ref = await this.ctx.storage.get(`payoffer:${offerId}`);
    if (!ref) return null;
    const entry = await this.ctx.storage.get(`pay:${ref}`);
    return entry || null;
  }

  async payEvent({ reference, event, verifiedAmount }) {
    const entry = await this.ctx.storage.get(`pay:${reference}`);
    if (!entry) return { ok: false, error: "UNKNOWN_REFERENCE" };
    const env = this.env;

    if (event === "payment.success") {
      if (entry.status === "paid") return { ok: true, already: true, published: true };
      if (typeof verifiedAmount === "number" && verifiedAmount < entry.amount) {
        return { ok: false, error: "AMOUNT_MISMATCH" };
      }
      let published = false;
      if (getServiceAccount(env)) {
        const r = await adminPatchDoc(env, `offers/${entry.offerId}`, {
          status: env.OFFER_ACTIVE_STATUS || "active",
          paymentStatus: "paid",
          paymentRef: reference,
          paidAt: new Date(),
          updatedAt: new Date()
        });
        if (!r.ok) {
          entry.status = "paid_unpublished";
          await this.ctx.storage.put(`pay:${reference}`, entry);
          return { ok: false, error: "OFFER_PUBLISH_FAILED" };
        }
        published = true;
      }
      entry.status = published ? "paid" : "paid_unpublished";
      entry.paidAt = Date.now();
      await this.ctx.storage.put(`pay:${reference}`, entry);
      await this.push({ type: "payment", status: "paid", reference, offerId: entry.offerId, published });
      if (published) {
        await this.fcm({ kind: "payment", title: "Annonce publiée", body: "Votre paiement est confirmé, votre annonce est en ligne.", data: { kind: "payment", status: "paid", offerId: entry.offerId, reference } });
      }
      return { ok: true, published, warning: published ? undefined : "NO_SERVICE_ACCOUNT" };
    }

    const map = { "payment.failed": "failed", "payment.cancelled": "cancelled", "payment.expired": "expired", "payment.refunded": "refunded" };
    const status = map[event];
    if (status) {
      if (entry.status !== "paid" || status === "refunded") {
        entry.status = status;
        await this.ctx.storage.put(`pay:${reference}`, entry);
        await this.push({ type: "payment", status, reference, offerId: entry.offerId });
      }
      return { ok: true, status };
    }
    return { ok: true, ignored: true };
  }
}

/* ---------------------------------------------------------------------------
 *  Durable Object : une salle de signalisation par appel
 * ------------------------------------------------------------------------- */
export class CallRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.buckets = new WeakMap();
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  live() { return this.ctx.getWebSockets().filter((ws) => ws.readyState === WS_OPEN); }
  closeQuietly(ws, code, reason) { try { ws.close(code, reason); } catch { /* ignore */ } }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/connect") return this.connect(request);
    if (url.pathname === "/arm" && request.method === "POST") return this.arm(request);
    return new Response("Not found", { status: 404 });
  }

  headersMeta(request) {
    return {
      callId: request.headers.get("X-Brav-Call-Id"),
      callerId: request.headers.get("X-Brav-Caller"),
      receiverId: request.headers.get("X-Brav-Receiver"),
      callType: request.headers.get("X-Brav-Call-Type") || "audio"
    };
  }

  async schedule(kind, at) {
    const plan = (await this.ctx.storage.get("plan")) || {};
    plan[kind] = at;
    await this.ctx.storage.put("plan", plan);
    await this.ctx.storage.setAlarm(Math.min(...Object.values(plan)));
  }

  async unschedule(kind) {
    const plan = (await this.ctx.storage.get("plan")) || {};
    if (!(kind in plan)) return;
    delete plan[kind];
    await this.ctx.storage.put("plan", plan);
    const times = Object.values(plan);
    if (times.length) await this.ctx.storage.setAlarm(Math.min(...times));
    else await this.ctx.storage.deleteAlarm();
  }

  async arm(request) {
    const m = this.headersMeta(request);
    if (!m.callId || !m.callerId || !m.receiverId) return new Response("Bad request", { status: 400 });
    const body = await request.json().catch(() => ({}));
    const existing = (await this.ctx.storage.get("meta")) || {};
    await this.ctx.storage.put("meta", {
      ...existing, callId: m.callId, callerId: m.callerId, receiverId: m.receiverId,
      callerName: String(body.callerName || "").slice(0, 80), callType: body.callType === "video" ? "video" : "audio"
    });
    await this.schedule("ring-timeout", Date.now() + Math.min(Math.max(Number(body.delayMs) || RING_TIMEOUT_MS, 10000), 120000));
    await this.schedule("expire", Date.now() + MAX_CALL_MS);
    return new Response(JSON.stringify({ ok: true }), { headers: { "Content-Type": "application/json" } });
  }

  async connect(request) {
    if ((request.headers.get("Upgrade") || "").toLowerCase() !== "websocket") return new Response("WebSocket required", { status: 426 });
    const uid = request.headers.get("X-Brav-Uid");
    const m = this.headersMeta(request);
    if (!uid || !m.callId || !m.callerId || !m.receiverId || (uid !== m.callerId && uid !== m.receiverId)) {
      return new Response("Forbidden", { status: 403 });
    }
    const peerUid = uid === m.callerId ? m.receiverId : m.callerId;
    const previous = this.live();

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ uid, callId: m.callId, connectedAt: Date.now() });

    // Une reconnexion remplace l'ancienne connexion (réseau mobile instable).
    for (const ws of previous) {
      if (ws.deserializeAttachment()?.uid === uid) this.closeQuietly(ws, 4000, "replaced");
    }

    const meta = (await this.ctx.storage.get("meta")) || {};
    await this.ctx.storage.put("meta", { ...meta, callId: m.callId, callerId: m.callerId, receiverId: m.receiverId, callType: meta.callType || m.callType });
    await this.unschedule("cleanup");
    await this.schedule("expire", Date.now() + MAX_CALL_MS);

    const peerConnected = previous.some((ws) => ws.readyState === WS_OPEN && ws.deserializeAttachment()?.uid === peerUid);
    server.send(JSON.stringify({ type: "room-ready", callId: m.callId, uid, peerUid, callType: m.callType, peerConnected }));
    if (peerConnected) this.sendTo(peerUid, JSON.stringify({ type: "peer-joined", callId: m.callId, uid }));
    await this.flushQueue(server, uid);
    return new Response(null, { status: 101, webSocket: client });
  }

  sendTo(uid, raw) {
    let delivered = 0;
    for (const ws of this.live()) {
      if (ws.deserializeAttachment()?.uid !== uid) continue;
      try { ws.send(raw); delivered++; } catch { this.closeQuietly(ws, 1011, "delivery failed"); }
    }
    return delivered;
  }

  async flushQueue(ws, uid) {
    const key = `q:${uid}`;
    const queue = (await this.ctx.storage.get(key)) || [];
    if (!queue.length) return;
    await this.ctx.storage.delete(key);
    const now = Date.now();
    for (const item of queue) {
      if (now - item.at > QUEUE_TTL_MS) continue;
      try { ws.send(item.raw); } catch { break; }
    }
  }

  async enqueue(uid, raw) {
    const key = `q:${uid}`;
    const queue = ((await this.ctx.storage.get(key)) || []).filter((i) => Date.now() - i.at <= QUEUE_TTL_MS);
    queue.push({ at: Date.now(), raw });
    while (queue.length > QUEUE_MAX_ITEMS) queue.shift();
    await this.ctx.storage.put(key, queue);
  }

  async webSocketMessage(ws, raw) {
    const att = ws.deserializeAttachment();
    if (!att) return this.closeQuietly(ws, 1011, "missing session");
    const text = typeof raw === "string" ? raw : dec.decode(raw);
    if (enc.encode(text).length > MAX_SIGNAL_BYTES) return this.closeQuietly(ws, 1009, "signal payload too large");

    let bucket = this.buckets.get(ws);
    const now = Date.now();
    if (!bucket) { bucket = { tokens: 120, last: now }; this.buckets.set(ws, bucket); }
    bucket.tokens = Math.min(120, bucket.tokens + ((now - bucket.last) / 1000) * 60);
    bucket.last = now;
    if (bucket.tokens < 1) return ws.send(JSON.stringify({ type: "error", error: "RATE_LIMITED" }));
    bucket.tokens -= 1;

    let msg;
    try { msg = JSON.parse(text); } catch { return ws.send(JSON.stringify({ type: "error", error: "INVALID_JSON" })); }
    if (!msg || !SIGNAL_TYPES.has(msg.type)) return ws.send(JSON.stringify({ type: "error", error: "UNSUPPORTED_SIGNAL_TYPE" }));
    if (msg.type === "ping") return ws.send(JSON.stringify({ type: "pong", timestamp: Date.now() }));

    if (msg.type === "hangup") {
      msg.payload = msg.payload && typeof msg.payload === "object" ? msg.payload : {};
    } else if (!msg.payload || typeof msg.payload !== "object" || Array.isArray(msg.payload)) {
      return ws.send(JSON.stringify({ type: "error", error: "INVALID_PAYLOAD", id: msg.id }));
    }
    if ((msg.type === "offer" || msg.type === "answer") && (typeof msg.payload.sdp !== "string" || msg.payload.sdp.length > 24000)) {
      return ws.send(JSON.stringify({ type: "error", error: "INVALID_SDP", id: msg.id }));
    }
    if (msg.type === "ice-candidate" && msg.payload.candidate !== undefined && msg.payload.candidate !== null &&
        (typeof msg.payload.candidate !== "string" || msg.payload.candidate.length > 2000)) {
      return ws.send(JSON.stringify({ type: "error", error: "INVALID_CANDIDATE", id: msg.id }));
    }
    if (msg.type === "call-state" && !CALL_STATES.has(msg.payload.state)) {
      return ws.send(JSON.stringify({ type: "error", error: "INVALID_CALL_STATE", id: msg.id }));
    }

    const meta = (await this.ctx.storage.get("meta")) || {};
    const peerUid = att.uid === meta.callerId ? meta.receiverId : meta.callerId;
    const outgoing = JSON.stringify({
      type: msg.type, callId: att.callId, fromUid: att.uid, payload: msg.payload, sentAt: Date.now()
    });

    const delivered = this.sendTo(peerUid, outgoing) > 0;
    let queued = false;
    if (!delivered && QUEUEABLE_TYPES.has(msg.type)) { await this.enqueue(peerUid, outgoing); queued = true; }
    if (msg.id !== undefined) ws.send(JSON.stringify({ type: "ack", id: msg.id, delivered, queued }));

    if (msg.type === "hangup" || (msg.type === "call-state" && msg.payload.state === "ended")) {
      await this.unschedule("ring-timeout");
      await this.schedule("cleanup", Date.now() + 30000);
    }
    if (msg.type === "call-state" && msg.payload.state === "connected") await this.unschedule("ring-timeout");
    if (msg.type === "answer") await this.unschedule("ring-timeout");
  }

  async webSocketClose(ws, code, reason) {
    const att = ws.deserializeAttachment();
    this.closeQuietly(ws, code && ![1005, 1006, 1015].includes(code) ? code : 1000, reason || "closed");
    if (att) {
      const sameUserStill = this.live().some((s) => s !== ws && s.deserializeAttachment()?.uid === att.uid);
      if (!sameUserStill) {
        const meta = (await this.ctx.storage.get("meta")) || {};
        const peerUid = att.uid === meta.callerId ? meta.receiverId : meta.callerId;
        if (peerUid) this.sendTo(peerUid, JSON.stringify({ type: "peer-left", callId: att.callId, uid: att.uid }));
      }
    }
    if (this.live().filter((s) => s !== ws).length === 0) await this.schedule("cleanup", Date.now() + 120000);
  }

  async webSocketError(ws) { this.closeQuietly(ws, 1011, "websocket error"); }

  async alarm() {
    const plan = (await this.ctx.storage.get("plan")) || {};
    const now = Date.now();
    const due = Object.entries(plan).filter(([, at]) => at <= now).map(([k]) => k);
    for (const k of due) delete plan[k];
    await this.ctx.storage.put("plan", plan);
    const rest = Object.values(plan);
    if (rest.length) await this.ctx.storage.setAlarm(Math.min(...rest));

    for (const kind of due) {
      try {
        if (kind === "ring-timeout") await this.ringTimeout();
        else if (kind === "expire") { for (const ws of this.live()) this.closeQuietly(ws, 4002, "call expired"); await this.ctx.storage.deleteAll(); }
        else if (kind === "cleanup" && this.live().length === 0) await this.ctx.storage.deleteAll();
      } catch (e) { log("warn", "call-room alarm failed", { kind, message: String(e && e.message) }); }
    }
  }

  async ringTimeout() {
    const meta = await this.ctx.storage.get("meta");
    if (!meta || !meta.callId || !getServiceAccount(this.env)) return;
    const r = await adminGetDoc(this.env, `calls/${meta.callId}`);
    if (!r.ok || r.data.status !== "calling") return;
    const patch = await adminPatchDoc(this.env, `calls/${meta.callId}`,
      { status: "ended", endedAt: new Date(), durationSeconds: 0, missed: true }, { updateTime: r.updateTime });
    if (!patch.ok) return;
    const room = JSON.stringify({ type: "call-state", callId: meta.callId, fromUid: "server", payload: { state: "ended", reason: "no_answer" }, sentAt: Date.now() });
    for (const ws of this.live()) { try { ws.send(room); } catch { /* ignore */ } }
    await hubCall(this.env, meta.callerId, "/push", { event: { type: "call_ended", callId: meta.callId, reason: "no_answer", sentAt: Date.now() } });
    await hubCall(this.env, meta.receiverId, "/push", { event: { type: "missed_call", callId: meta.callId, callerId: meta.callerId, callerName: meta.callerName || "", callType: meta.callType, sentAt: Date.now() } });
    await hubCall(this.env, meta.receiverId, "/fcm", {
      kind: "missed", title: "Appel manqué", body: meta.callerName ? `Appel manqué de ${meta.callerName}` : "Vous avez manqué un appel",
      data: { kind: "missed_call", callId: meta.callId, callerId: meta.callerId, callerName: meta.callerName || "" }
    });
  }
}

/* ============================================================================
 *  TURN / STUN / SFU (Cloudflare Realtime)
 * ========================================================================== */

const turnCache = new Map();

async function turnCredentials(env, uid, ttl) {
  const cached = turnCache.get(uid);
  if (cached && Date.now() - cached.createdAt < 10 * 60 * 1000 && cached.ttl === ttl) return cached.value;

  let result;
  if (env.TURN_KEY_ID && env.TURN_KEY_API_TOKEN) {
    let res;
    try {
      res = await fetch(`https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(env.TURN_KEY_ID)}/credentials/generate-ice-servers`, {
        method: "POST",
        headers: { Authorization: `Bearer ${env.TURN_KEY_API_TOKEN}`, "Content-Type": "application/json" },
        body: JSON.stringify({ ttl })
      });
    } catch { throw new HttpError(502, "TURN_PROVIDER_UNREACHABLE"); }
    if (!res.ok) {
      log("error", "cloudflare turn error", { status: res.status });
      throw new HttpError(502, "TURN_PROVIDER_ERROR");
    }
    const data = await res.json();
    const ice = Array.isArray(data.iceServers) ? data.iceServers : (data.iceServers ? [data.iceServers] : []);
    if (!ice.length) throw new HttpError(502, "TURN_PROVIDER_ERROR");
    result = { provider: "cloudflare", relay: true, iceServers: ice, expiresAt: Math.floor(Date.now() / 1000) + ttl };
  } else if (splitCsv(env.TURN_URLS).length && env.TURN_SHARED_SECRET) {
    const expiresAt = Math.floor(Date.now() / 1000) + ttl;
    const username = `${expiresAt}:${uid}`;
    const credential = bytesToB64(await hmac("SHA-1", env.TURN_SHARED_SECRET, username));
    const ice = [];
    const stun = splitCsv(env.STUN_URLS || "stun:stun.cloudflare.com:3478");
    if (stun.length) ice.push({ urls: stun });
    ice.push({ urls: splitCsv(env.TURN_URLS), username, credential });
    result = { provider: "coturn", relay: true, iceServers: ice, expiresAt };
  } else {
    result = {
      provider: "stun-only", relay: false,
      iceServers: [{ urls: splitCsv(env.STUN_URLS || "stun:stun.cloudflare.com:3478") }],
      expiresAt: Math.floor(Date.now() / 1000) + ttl,
      warning: "TURN non configuré : les appels entre réseaux mobiles (CGNAT) peuvent échouer. Définissez TURN_KEY_ID et TURN_KEY_API_TOKEN."
    };
  }
  turnCache.set(uid, { createdAt: Date.now(), ttl, value: result });
  if (turnCache.size > 2000) turnCache.clear();
  return result;
}

const SFU_ALLOWED = [
  /^sessions\/new$/,
  /^sessions\/[A-Za-z0-9_-]{1,128}$/,
  /^sessions\/[A-Za-z0-9_-]{1,128}\/(tracks\/(new|update|close)|renegotiate)$/,
  /^sessions\/[A-Za-z0-9_-]{1,128}\/datachannels\/(establish|new|update|close)$/
];

async function sfuProxy(request, env, callId, rest, cors) {
  if (!env.CF_REALTIME_APP_ID || !env.CF_REALTIME_APP_SECRET) throw new HttpError(503, "SFU_NOT_CONFIGURED");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "sfu", 240, 60000);
  if (!SFU_ALLOWED.some((re) => re.test(rest))) throw new HttpError(404, "SFU_ENDPOINT_NOT_ALLOWED");
  if (!["GET", "POST", "PUT"].includes(request.method)) throw new HttpError(405, "METHOD_NOT_ALLOWED");
  const call = await loadCall(env, a, callId);
  if (!call.ok) throw new HttpError(call.status, call.error);
  if (!["calling", "accepted"].includes(call.status)) throw new HttpError(409, "CALL_NOT_ACTIVE");

  const init = { method: request.method, headers: { Authorization: `Bearer ${env.CF_REALTIME_APP_SECRET}` } };
  if (request.method !== "GET") {
    const body = await request.text();
    if (enc.encode(body).length > 64 * 1024) throw new HttpError(413, "BODY_TOO_LARGE");
    init.headers["Content-Type"] = "application/json";
    init.body = body || undefined;
  }
  let res;
  try { res = await fetch(`https://rtc.live.cloudflare.com/v1/apps/${encodeURIComponent(env.CF_REALTIME_APP_ID)}/${rest}${new URL(request.url).search}`, init); }
  catch { throw new HttpError(502, "SFU_UNREACHABLE"); }
  const headers = new Headers(cors);
  headers.set("Content-Type", res.headers.get("Content-Type") || "application/json");
  headers.set("Cache-Control", "no-store");
  return new Response(await res.text(), { status: res.status, headers });
}

/* ============================================================================
 *  MESSAGES : notification temps réel + push
 * ========================================================================== */

const participantCache = new Map();

async function loadConversation(env, auth, conversationId) {
  const key = `${auth.uid}:${conversationId}`;
  const hit = cacheGet(`conv:${key}`);
  if (hit) return hit;
  const doc = await fsGetDoc(env, `conversations/${conversationId}`, auth.token);
  if (!doc.ok) return doc;
  const participants = Array.isArray(doc.data.participants) ? doc.data.participants : [];
  if (!participants.includes(auth.uid)) return fail(403, "NOT_A_PARTICIPANT");
  const result = { ok: true, participants, names: doc.data.participantNames || {} };
  cacheSet(`conv:${key}`, result, 120000);
  return result;
}
void participantCache;

function previewFor(type, content) {
  switch (type) {
    case "image": return "📷 Photo";
    case "audio": case "voice": return "🎤 Message vocal";
    case "video": return "🎥 Vidéo";
    case "file": case "document": return "📎 Document";
    default: return String(content || "").slice(0, 140);
  }
}

async function chatNotify(request, env, cors) {
  if (!env.USER_HUBS) throw new HttpError(503, "USER_HUBS_BINDING_MISSING");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "notify", 120, 60000);
  const body = await readJson(request);
  if (!isValidId(body.conversationId) || !isValidId(body.messageId)) throw new HttpError(400, "INVALID_IDS");

  const [msgDoc, conv] = await Promise.all([
    fsGetDoc(env, `conversations/${body.conversationId}/messages/${body.messageId}`, a.token),
    loadConversation(env, a, body.conversationId)
  ]);
  if (!conv.ok) throw new HttpError(conv.status, conv.error);
  if (!msgDoc.ok) throw new HttpError(msgDoc.status, msgDoc.error);
  const m = msgDoc.data;
  if (m.senderId !== a.uid) throw new HttpError(403, "NOT_THE_SENDER");
  if (!m.receiverId || !conv.participants.includes(m.receiverId)) throw new HttpError(422, "INVALID_RECEIVER");

  const senderName = conv.names[a.uid] || a.claims.name || "Nouveau message";
  const messageType = m.messageType || "text";
  const preview = previewFor(messageType, m.content);
  const event = {
    type: "message", conversationId: body.conversationId, messageId: body.messageId,
    senderId: a.uid, senderName, messageType, preview, timestamp: m.timestamp || null, sentAt: Date.now()
  };
  const live = await hubCall(env, m.receiverId, "/push", { event });
  const delivered = (live.data && live.data.delivered) || 0;

  let pushed = 0;
  if (delivered === 0) {
    const r = await hubCall(env, m.receiverId, "/fcm", {
      kind: "message", title: senderName, body: preview,
      data: { kind: "message", conversationId: body.conversationId, messageId: body.messageId, senderId: a.uid, senderName, messageType }
    });
    pushed = (r.data && r.data.sent) || 0;
  }
  return json({ ok: true, delivered, pushed }, 200, cors);
}

async function manageDevice(request, env, cors, method) {
  if (!env.USER_HUBS) throw new HttpError(503, "USER_HUBS_BINDING_MISSING");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "devices", 30, 60000);
  const body = await readJson(request);
  const r = await hubCall(env, a.uid, "/devices", { token: body.token, platform: body.platform }, method);
  if (!r.data || !r.data.ok) throw new HttpError(400, (r.data && r.data.error) || "INVALID_TOKEN");
  return json({ ok: true, devices: r.data.count }, 200, cors);
}

async function presenceQuery(request, env, url, cors) {
  if (!env.USER_HUBS) throw new HttpError(503, "USER_HUBS_BINDING_MISSING");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "presence", 60, 60000);
  const uids = [...new Set(splitCsv(url.searchParams.get("uids")).filter(isValidId))].slice(0, 100);
  const users = await Promise.all(uids.map(async (u) => {
    try { const r = await hubCall(env, u, "/presence", null, "GET"); return r.data ? { uid: u, online: r.data.online, lastSeen: r.data.lastSeen } : { uid: u, online: false, lastSeen: 0 }; }
    catch { return { uid: u, online: false, lastSeen: 0 }; }
  }));
  return json({ users }, 200, cors);
}

/* ============================================================================
 *  MÉDIAS (R2) : photos, vocaux, vidéos, PDF
 * ========================================================================== */

function uploadLimitMB(env) { return Math.min(Math.max(Number(env.MAX_UPLOAD_MB) || 25, 1), 90); }

async function mediaUpload(request, env, url, cors) {
  if (!env.MEDIA) throw new HttpError(503, "MEDIA_NOT_CONFIGURED");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "upload", 30, 60000);
  const conversationId = url.searchParams.get("conversationId") || "";
  if (!isValidId(conversationId)) throw new HttpError(400, "INVALID_CONVERSATION_ID");
  const conv = await loadConversation(env, a, conversationId);
  if (!conv.ok) throw new HttpError(conv.status, conv.error);

  const contentType = (request.headers.get("Content-Type") || "").split(";")[0].trim().toLowerCase();
  const ext = MEDIA_TYPES[contentType];
  if (!ext) throw new HttpError(415, "UNSUPPORTED_MEDIA_TYPE", { allowed: Object.keys(MEDIA_TYPES) });
  const length = Number(request.headers.get("Content-Length"));
  if (!Number.isFinite(length) || length <= 0) throw new HttpError(411, "CONTENT_LENGTH_REQUIRED");
  if (length > uploadLimitMB(env) * 1024 * 1024) throw new HttpError(413, "FILE_TOO_LARGE", { maxMB: uploadLimitMB(env) });
  if (!request.body) throw new HttpError(400, "EMPTY_BODY");

  const name = `${Date.now().toString(36)}-${randomHex(8)}.${ext}`;
  const key = `c/${conversationId}/${name}`;
  await env.MEDIA.put(key, request.body, {
    httpMetadata: { contentType },
    customMetadata: { uid: a.uid, conversationId }
  });
  return json({ ok: true, key, url: `${PUBLIC_ORIGIN}/v1/media/${key}`, size: length, contentType }, 201, cors);
}

async function mediaDownload(request, env, conversationId, name, cors) {
  if (!env.MEDIA) throw new HttpError(503, "MEDIA_NOT_CONFIGURED");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "download", 600, 60000);
  const conv = await loadConversation(env, a, conversationId);
  if (!conv.ok) throw new HttpError(conv.status, conv.error);
  const key = `c/${conversationId}/${name}`;

  const object = await env.MEDIA.get(key, { range: request.headers, onlyIf: request.headers });
  if (!object) throw new HttpError(404, "MEDIA_NOT_FOUND");

  const headers = new Headers(cors);
  object.writeHttpMetadata(headers);
  headers.set("ETag", object.httpEtag);
  headers.set("Accept-Ranges", "bytes");
  headers.set("Cache-Control", "private, max-age=3600");
  headers.set("X-Content-Type-Options", "nosniff");
  const type = headers.get("Content-Type") || "application/octet-stream";
  headers.set("Content-Disposition", INLINE_MEDIA.test(type) ? "inline" : `attachment; filename="${name}"`);

  if (!("body" in object) || !object.body) return new Response(null, { status: 304, headers });
  let status = 200;
  if (object.range && request.headers.get("Range")) {
    const r = object.range;
    const offset = r.offset ?? Math.max(object.size - (r.suffix || 0), 0);
    const length = r.length ?? (object.size - offset);
    headers.set("Content-Range", `bytes ${offset}-${offset + length - 1}/${object.size}`);
    headers.set("Content-Length", String(length));
    status = 206;
  } else {
    headers.set("Content-Length", String(object.size));
  }
  return new Response(request.method === "HEAD" ? null : object.body, { status, headers });
}

async function mediaDelete(request, env, conversationId, name, cors) {
  if (!env.MEDIA) throw new HttpError(503, "MEDIA_NOT_CONFIGURED");
  const a = await requireAuth(request, env);
  const conv = await loadConversation(env, a, conversationId);
  if (!conv.ok) throw new HttpError(conv.status, conv.error);
  const key = `c/${conversationId}/${name}`;
  const head = await env.MEDIA.head(key);
  if (!head) throw new HttpError(404, "MEDIA_NOT_FOUND");
  if (head.customMetadata?.uid !== a.uid) throw new HttpError(403, "NOT_THE_UPLOADER");
  await env.MEDIA.delete(key);
  return json({ ok: true }, 200, cors);
}

/* ============================================================================
 *  RECHERCHE INSTANTANÉE (utilisateurs + annonces)
 * ========================================================================== */

function norm(s) {
  return String(s || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9\s+]/g, " ").replace(/\s+/g, " ").trim();
}

function normalizePhone(raw) {
  const t = String(raw || "").trim();
  if (!t) return "";
  const digits = t.replace(/\D/g, "");
  if (!digits) return "";
  if (t.startsWith("+")) return `+${digits}`;
  if (digits.startsWith("00") && digits.length > 2) return `+${digits.slice(2)}`;
  if (digits.length === 10 && /^(07|05|01)/.test(digits)) return `+225${digits}`;
  if (digits.length === 8 && /^[0457]/.test(digits)) return `+225${digits}`;
  return digits;
}

function scoreText(haystackNorm, tokens, weight) {
  let score = 0;
  for (const t of tokens) {
    if (!haystackNorm) return 0;
    if (haystackNorm.split(" ").some((w) => w.startsWith(t))) score += weight * 2;
    else if (haystackNorm.includes(t)) score += weight;
    else return -1;
  }
  return score;
}

function nameVariants(q) {
  const title = q.split(/\s+/).map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(" ");
  const first = q.charAt(0).toUpperCase() + q.slice(1);
  return [...new Set([q, first, q.toUpperCase(), q.toLowerCase(), title])].slice(0, 5);
}

async function searchUsers(env, auth, q, limit) {
  const phone = normalizePhone(q);
  const isPhone = /^[+\d][\d\s+.-]{5,}$/.test(q) && phone.replace(/\D/g, "").length >= 6;
  const queries = [];
  if (isPhone) {
    const digits = phone.replace(/\D/g, "");
    const variants = [...new Set([phone, digits, `+${digits}`, digits.length === 12 ? `0${digits.slice(3)}` : null].filter(Boolean))].slice(0, 10);
    queries.push({
      from: [{ collectionId: "users" }],
      where: { fieldFilter: { field: { fieldPath: "phoneNumber" }, op: "IN", value: { arrayValue: { values: variants.map((v) => ({ stringValue: v })) } } } },
      limit
    });
  } else {
    for (const v of nameVariants(q)) {
      queries.push({
        from: [{ collectionId: "users" }],
        where: { compositeFilter: { op: "AND", filters: [
          { fieldFilter: { field: { fieldPath: "displayName" }, op: "GREATER_THAN_OR_EQUAL", value: { stringValue: v } } },
          { fieldFilter: { field: { fieldPath: "displayName" }, op: "LESS_THAN", value: { stringValue: `${v}\uf8ff` } } }
        ] } },
        orderBy: [{ field: { fieldPath: "displayName" } }],
        limit
      });
    }
  }
  const results = await Promise.all(queries.map((sq) => fsRunQuery(env, sq, auth.token)));
  const seen = new Map();
  for (const r of results) if (r.ok) for (const d of r.docs) seen.set(d.userId || d.id, d);
  if (results.every((r) => !r.ok)) throw new HttpError(results[0].status || 502, results[0].error || "SEARCH_FAILED");

  const tokens = norm(q).split(" ").filter(Boolean);
  const out = [];
  for (const d of seen.values()) {
    const uid = d.userId || d.id;
    if (uid === auth.uid) continue;
    let score = isPhone ? 10 : scoreText(norm(d.displayName), tokens, 3);
    if (score < 0) continue;
    if (norm(d.displayName).startsWith(norm(q))) score += 4;
    out.push({
      score,
      user: {
        userId: uid, displayName: d.displayName || "", avatarUrl: d.avatarUrl || "", bio: d.bio || "",
        role: d.role || "user", locationCity: d.locationCity || "", locationDetails: d.locationDetails || "",
        isOnline: Boolean(d.isOnline), ...(isPhone ? { phoneNumber: d.phoneNumber || "" } : {})
      }
    });
  }
  return out.sort((x, y) => y.score - x.score).slice(0, limit).map((x) => x.user);
}

async function loadOffers(env, auth) {
  const cached = cacheGet("offers:list");
  if (cached) return cached;
  const r = await fsRunQuery(env, {
    from: [{ collectionId: "offers" }],
    orderBy: [{ field: { fieldPath: "createdAt" }, direction: "DESCENDING" }],
    limit: 300
  }, auth.token);
  if (!r.ok) throw new HttpError(r.status, r.error);
  cacheSet("offers:list", r.docs, 15000);
  return r.docs;
}

async function searchOffers(env, auth, q, { city, category, limit }) {
  const hidden = new Set(splitCsv(env.OFFER_HIDDEN_STATUSES || "pending_payment,unpaid,pending,draft,expired,rejected,deleted,closed,archived").map((s) => s.toLowerCase()));
  const all = await loadOffers(env, auth);
  const tokens = norm(q).split(" ").filter(Boolean);
  const cityN = norm(city), catN = norm(category);
  const out = [];
  for (const o of all) {
    if (o.status && hidden.has(String(o.status).toLowerCase())) continue;
    if (cityN && !norm(o.locationCity).includes(cityN)) continue;
    if (catN && !norm(o.category).includes(catN)) continue;
    let score = 1;
    if (tokens.length) {
      const parts = [scoreText(norm(o.title), tokens, 5), scoreText(norm(o.category), tokens, 3),
        scoreText(norm(o.locationCity), tokens, 2), scoreText(norm(o.description), tokens, 1), scoreText(norm(o.authorName), tokens, 1)];
      // chaque mot doit se retrouver quelque part (titre, catégorie, ville, description, auteur)
      const combined = norm(`${o.title} ${o.category} ${o.locationCity} ${o.description} ${o.authorName}`);
      if (!tokens.every((t) => combined.includes(t))) continue;
      score = parts.filter((p) => p > 0).reduce((s, p) => s + p, 0) || 1;
    }
    out.push({
      score,
      offer: {
        id: o.id, title: o.title || "", category: o.category || "", locationCity: o.locationCity || "",
        priceOrSalary: o.priceOrSalary || "", imageUrl: o.imageUrl || "", authorName: o.authorName || "",
        userId: o.userId || "", contactPhone: o.contactPhone || "", status: o.status || "",
        description: String(o.description || "").slice(0, 200), createdAt: o.createdAt || null
      }
    });
  }
  out.sort((x, y) => y.score - x.score || String(y.offer.createdAt).localeCompare(String(x.offer.createdAt)));
  return out.slice(0, limit).map((x) => x.offer);
}

async function search(request, env, url, cors) {
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "search", 120, 60000);
  const q = (url.searchParams.get("q") || "").trim().slice(0, 64);
  const type = url.searchParams.get("type") || "all";
  if (!["users", "offers", "all"].includes(type)) throw new HttpError(400, "INVALID_TYPE");
  const limit = Math.min(Math.max(parseInt(url.searchParams.get("limit"), 10) || 20, 1), 50);
  const city = (url.searchParams.get("city") || "").slice(0, 64);
  const category = (url.searchParams.get("category") || "").slice(0, 64);
  const hasFilter = Boolean(city || category);
  if (q.length < 2 && !(hasFilter && type !== "users")) throw new HttpError(400, "QUERY_TOO_SHORT");

  const out = { query: q };
  const jobs = [];
  if (type === "users" || type === "all") {
    if (q.length >= 2) jobs.push(searchUsers(env, a, q, limit).then((r) => { out.users = r; }));
  }
  if (type === "offers" || type === "all") jobs.push(searchOffers(env, a, q, { city, category, limit }).then((r) => { out.offers = r; }));
  await Promise.all(jobs);
  return json(out, 200, cors);
}

/* ============================================================================
 *  PAIEMENT D'UNE ANNONCE — GeniusPay
 *  (seule la personne qui publie une annonce paie ; rien d'autre n'est payant)
 * ========================================================================== */

function geniusBase(env) { return (env.GENIUSPAY_API_BASE || "https://geniuspay.ci/api/v1/merchant").replace(/\/+$/, ""); }

async function geniusFetch(env, path, init = {}) {
  const res = await fetch(`${geniusBase(env)}${path}`, {
    ...init,
    headers: {
      "X-API-Key": env.GENIUSPAY_API_KEY,
      "X-API-Secret": env.GENIUSPAY_API_SECRET,
      "Content-Type": "application/json",
      "Accept": "application/json",
      ...(init.headers || {})
    }
  });
  let body = null;
  try { body = await res.json(); } catch { /* ignore */ }
  return { ok: res.ok, status: res.status, body, data: body && typeof body === "object" ? (body.data && typeof body.data === "object" ? body.data : body) : null };
}

async function createListingPayment(request, env, cors) {
  if (!paymentsConfigured(env)) throw new HttpError(503, "PAYMENTS_NOT_CONFIGURED");
  if (!env.USER_HUBS) throw new HttpError(503, "USER_HUBS_BINDING_MISSING");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "pay", 6, 60000);
  const body = await readJson(request);
  if (!isValidId(body.offerId)) throw new HttpError(400, "INVALID_OFFER_ID");

  const offer = await fsGetDoc(env, `offers/${body.offerId}`, a.token);
  if (!offer.ok) throw new HttpError(offer.status, offer.error);
  if (offer.data.userId !== a.uid || offer.data.isExternal === true) throw new HttpError(403, "NOT_THE_OFFER_OWNER");
  if (offer.data.paymentStatus === "paid") throw new HttpError(409, "ALREADY_PAID");

  const existing = await hubCall(env, a.uid, "/pay/find", { offerId: body.offerId });
  const e = existing.data;
  if (e && e.status === "pending" && Date.now() - e.createdAt < 15 * 60 * 1000 && e.checkoutUrl) {
    return json({ ok: true, reference: e.reference, checkoutUrl: e.checkoutUrl, amount: e.amount, currency: "XOF", reused: true }, 200, cors);
  }

  const amount = Math.max(Number(env.LISTING_PRICE_XOF) || 500, 200);
  const customer = { country: "CI" };
  const name = String(body.name || a.claims.name || "").slice(0, 80);
  const phone = normalizePhone(body.phone || a.claims.phone_number || offer.data.contactPhone || "");
  const email = a.claims.email || offer.data.contactEmail || "";
  if (name) customer.name = name;
  if (phone && phone.startsWith("+")) customer.phone = phone;
  if (email) customer.email = String(email).slice(0, 120);

  const r = await geniusFetch(env, "/payments", {
    method: "POST",
    body: JSON.stringify({
      amount, currency: "XOF",
      description: `Publication d'annonce BRAV CHAT`.slice(0, 500),
      customer,
      success_url: `${PUBLIC_ORIGIN}/pay/success`,
      error_url: `${PUBLIC_ORIGIN}/pay/error`,
      metadata: { app: "brav-chat", uid: a.uid, offerId: body.offerId }
    })
  });
  if (!r.ok || !r.data || !r.data.reference) {
    log("error", "geniuspay create failed", { status: r.status });
    throw new HttpError(r.status === 401 ? 502 : 502, "PAYMENT_PROVIDER_ERROR", { providerStatus: r.status });
  }
  const checkoutUrl = r.data.checkout_url || r.data.payment_url;
  if (!checkoutUrl) throw new HttpError(502, "PAYMENT_PROVIDER_ERROR");
  await hubCall(env, a.uid, "/pay/create", { reference: r.data.reference, offerId: body.offerId, amount, checkoutUrl });
  return json({ ok: true, reference: r.data.reference, checkoutUrl, amount, currency: "XOF" }, 201, cors);
}

async function finalizeFromProvider(env, uid, reference, event) {
  const check = await geniusFetch(env, `/payments/${encodeURIComponent(reference)}`);
  if (!check.ok || !check.data) return { ok: false, error: "PROVIDER_LOOKUP_FAILED" };
  const status = String(check.data.status || "");
  const amount = Number(check.data.amount);
  const effective = event || ({ paid: "payment.success", completed: "payment.success", failed: "payment.failed", cancelled: "payment.cancelled", expired: "payment.expired", refunded: "payment.refunded" })[status];
  if (effective === "payment.success" && !["paid", "completed"].includes(status)) return { ok: false, error: "PROVIDER_STATUS_MISMATCH" };
  if (!effective) return { ok: true, status };
  const r = await hubCall(env, uid, "/pay/event", { reference, event: effective, verifiedAmount: Number.isFinite(amount) ? amount : undefined });
  return r.data || { ok: false, error: "HUB_ERROR" };
}

async function getPayment(request, env, url, reference, cors) {
  if (!paymentsConfigured(env)) throw new HttpError(503, "PAYMENTS_NOT_CONFIGURED");
  const a = await requireAuth(request, env);
  enforceRate(a.uid, "pay-status", 60, 60000);
  let entry = (await hubCall(env, a.uid, "/pay/get", { reference })).data;
  if (!entry) throw new HttpError(404, "PAYMENT_NOT_FOUND");
  if (url.searchParams.get("refresh") === "1" && (entry.status === "pending" || entry.status === "paid_unpublished")) {
    await finalizeFromProvider(env, a.uid, reference, null);
    entry = (await hubCall(env, a.uid, "/pay/get", { reference })).data || entry;
  }
  return json({ reference, offerId: entry.offerId, status: entry.status, amount: entry.amount, currency: "XOF" }, 200, cors);
}

async function geniusPayWebhook(request, env, cors) {
  if (!paymentsConfigured(env) || !env.GENIUSPAY_WEBHOOK_SECRET) throw new HttpError(503, "WEBHOOK_NOT_CONFIGURED");
  const raw = await request.text();
  if (enc.encode(raw).length > 64 * 1024) throw new HttpError(413, "BODY_TOO_LARGE");
  const signature = (request.headers.get("X-Webhook-Signature") || "").replace(/^sha256=/i, "").trim().toLowerCase();
  const timestamp = request.headers.get("X-Webhook-Timestamp") || "";
  if (!signature || !timestamp) throw new HttpError(401, "MISSING_SIGNATURE");
  const ts = Number(timestamp);
  if (!Number.isFinite(ts) || Math.abs(Date.now() / 1000 - ts) > 300) throw new HttpError(401, "STALE_TIMESTAMP");
  const expected = bytesToHex(await hmac("SHA-256", env.GENIUSPAY_WEBHOOK_SECRET, `${Math.trunc(ts)}.${raw}`));
  if (!safeEqual(expected, signature)) throw new HttpError(401, "INVALID_SIGNATURE");

  let payload;
  try { payload = JSON.parse(raw); } catch { throw new HttpError(400, "INVALID_JSON"); }
  const data = payload && typeof payload.data === "object" && payload.data ? payload.data : payload;
  const event = request.headers.get("X-Webhook-Event") || payload.event || "";
  const reference = data && data.reference;
  const uid = data && data.metadata && data.metadata.uid;
  if (!reference || !isValidId(uid) || !env.USER_HUBS) return json({ received: true, ignored: true }, 200, cors);

  if (event === "payment.success") {
    const r = await finalizeFromProvider(env, uid, String(reference), "payment.success");
    if (!r.ok && r.error !== "UNKNOWN_REFERENCE") {
      log("error", "payment finalize failed", { reference, error: r.error });
      throw new HttpError(500, r.error || "FINALIZE_FAILED"); // GeniusPay réessaiera
    }
    return json({ received: true, published: Boolean(r.published) }, 200, cors);
  }
  await hubCall(env, uid, "/pay/event", { reference: String(reference), event });
  return json({ received: true }, 200, cors);
}

function payReturnPage(success) {
  const title = success ? "Paiement reçu" : "Paiement non abouti";
  const text = success
    ? "Merci ! Votre paiement est en cours de validation. Retournez dans l'application BRAV CHAT : votre annonce sera publiée dans un instant."
    : "Le paiement n'a pas abouti. Retournez dans l'application BRAV CHAT pour réessayer.";
  const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><style>body{font-family:system-ui,sans-serif;background:#0b1220;color:#e8eefc;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px}main{max-width:420px;text-align:center}h1{font-size:1.4rem}p{line-height:1.5;color:#b8c4e0}</style></head><body><main><h1>${success ? "✅" : "⚠️"} ${title}</h1><p>${text}</p></main></body></html>`;
  return new Response(html, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer"
    }
  });
}