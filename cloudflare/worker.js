/**
 * BRAV Chat Cloudflare Worker v4.0.0
 * Architecture 100% Cloudflare :
 * - Workers + Queues (TASKS_QUEUE)
 * - D1 Database: brav_chat (005a0119-2bb4-40ce-a50c-b3c3a461bc19)
 * - R2 Bucket: bravchat (WEUR)
 * - KV Namespace: cache, quotas, disjoncteur modèles IA, règles
 * - Durable Objects: UserHub (temps réel), CallRoom (signalisation WebRTC)
 * - Workers AI + AI Gateway : sélection intelligente + basculement automatique
 * - Firebase Auth JWT verification for project: brav-chat
 */

const WS_SUBPROTOCOL = "brav.v1";

export const FREE_AI_MODELS = [
  { id: "@cf/meta/llama-3.3-70b-instruct-fp8-fast", label: "Llama 3.3 70B Fast", category: "Raisonnement", intents: ["reasoning", "document", "chat"] },
  { id: "@cf/meta/llama-3.1-8b-instruct", label: "Llama 3.1 8B", category: "Général", intents: ["chat", "auto_reply"] },
  { id: "@cf/meta/llama-3.1-8b-instruct-fast", label: "Llama 3.1 8B Fast", category: "Rapide", intents: ["fast", "auto_reply"] },
  { id: "@cf/meta/llama-3-8b-instruct", label: "Llama 3 8B Instruct", category: "Général", intents: ["chat", "auto_reply"] },
  { id: "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b", label: "DeepSeek R1 Qwen 32B", category: "Maths LaTeX", intents: ["latex_math", "reasoning"] },
  { id: "@cf/qwen/qwq-32b", label: "Qwen QwQ 32B", category: "Maths LaTeX", intents: ["latex_math", "reasoning"] },
  { id: "@cf/qwen/qwen2.5-coder-32b-instruct", label: "Qwen 2.5 Coder 32B", category: "LaTeX & Structure", intents: ["coding", "latex_math", "document"] },
  { id: "@cf/qwen/qwen1.5-14b-chat-awq", label: "Qwen 1.5 14B Chat", category: "Multilingue", intents: ["translation", "chat"] },
  { id: "@cf/qwen/qwen1.5-7b-chat-awq", label: "Qwen 1.5 7B Chat", category: "Général", intents: ["translation", "chat"] },
  { id: "@cf/mistral/mistral-7b-instruct-v0.2", label: "Mistral 7B v0.2", category: "Rédaction", intents: ["document", "drafting"] },
  { id: "@cf/mistral/mistral-7b-instruct-v0.1", label: "Mistral 7B v0.1", category: "Général", intents: ["drafting", "chat"] },
  { id: "@cf/google/gemma-7b-it", label: "Gemma 7B IT", category: "Général", intents: ["chat"] },
  { id: "@cf/google/gemma-2b-it-lora", label: "Gemma 2B IT", category: "Rapide", intents: ["fast"] },
  { id: "@cf/meta/llama-3.2-3b-instruct", label: "Llama 3.2 3B Instruct", category: "Rapide", intents: ["fast", "auto_reply"] },
  { id: "@cf/meta/llama-3.2-1b-instruct", label: "Llama 3.2 1B", category: "Rapide", intents: ["fast"] },
  { id: "@cf/microsoft/phi-2", label: "Phi-2", category: "Maths LaTeX", intents: ["latex_math"] },
  { id: "@cf/tinyllama/tinyllama-1.1b-chat-v1.0", label: "TinyLlama 1.1B", category: "Rapide", intents: ["fast"] }
];

const ALLOWED_AI_MODEL_IDS = new Set(FREE_AI_MODELS.map((m) => m.id));

const INTENT_PRIORITY_CHAINS = {
  latex_math: [
    "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b",
    "@cf/qwen/qwq-32b",
    "@cf/qwen/qwen2.5-coder-32b-instruct",
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    "@cf/meta/llama-3.1-8b-instruct"
  ],
  document: [
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    "@cf/mistral/mistral-7b-instruct-v0.2",
    "@cf/qwen/qwen2.5-coder-32b-instruct",
    "@cf/meta/llama-3.1-8b-instruct"
  ],
  auto_reply: [
    "@cf/meta/llama-3.1-8b-instruct",
    "@cf/meta/llama-3.1-8b-instruct-fast",
    "@cf/meta/llama-3.2-3b-instruct",
    "@cf/mistral/mistral-7b-instruct-v0.2"
  ],
  translation: [
    "@cf/qwen/qwen1.5-14b-chat-awq",
    "@cf/meta/llama-3.1-8b-instruct",
    "@cf/mistral/mistral-7b-instruct-v0.2"
  ],
  reasoning: [
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b",
    "@cf/meta/llama-3.1-8b-instruct"
  ],
  coding: [
    "@cf/qwen/qwen2.5-coder-32b-instruct",
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    "@cf/meta/llama-3.1-8b-instruct"
  ],
  chat: [
    "@cf/meta/llama-3.1-8b-instruct",
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    "@cf/mistral/mistral-7b-instruct-v0.2",
    "@cf/qwen/qwen1.5-7b-chat-awq"
  ]
};

const DEFAULT_AI_FALLBACK_CHAIN = [
  "@cf/meta/llama-3.1-8b-instruct",
  "@cf/meta/llama-3-8b-instruct",
  "@cf/mistral/mistral-7b-instruct-v0.1",
  "@cf/qwen/qwen1.5-7b-chat-awq",
  "@cf/meta/llama-3.2-3b-instruct"
];

const ALLOWED_MEDIA_TYPES = [
  "image/jpeg", "image/png", "image/webp", "image/gif",
  "audio/mpeg", "audio/mp4", "audio/aac", "audio/ogg", "audio/webm", "audio/wav", "audio/x-wav", "audio/3gpp", "audio/amr",
  "video/mp4", "video/webm", "video/3gpp", "video/quicktime",
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/octet-stream"
];

let cachedJwks = null;
let cachedJwksExpiresAt = 0;

export function selectModelForIntent(intent = "chat", promptText = "", requestedModel = "") {
  const req = String(requestedModel || "").trim();
  if (req && req !== "auto" && ALLOWED_AI_MODEL_IDS.has(req)) {
    return req;
  }
  const normalizedIntent = String(intent || "").trim().toLowerCase();
  if (normalizedIntent && INTENT_PRIORITY_CHAINS[normalizedIntent]) {
    return INTENT_PRIORITY_CHAINS[normalizedIntent][0];
  }
  const lower = String(promptText || "").toLowerCase();
  if (/(\\frac|\\int|\\sum|\\lim|\\sqrt|\\begin\{|intégrale|dérivée|matrice|équation|équation différentielle|latex)/.test(lower)) {
    return INTENT_PRIORITY_CHAINS.latex_math[0];
  }
  if (/(pdf|word|\.docx|rapport|exposé|mémoire|fiche pédagogique|génère un document|rédige un document)/.test(lower)) {
    return INTENT_PRIORITY_CHAINS.document[0];
  }
  if (/(tradui|translate|translation)/.test(lower)) {
    return INTENT_PRIORITY_CHAINS.translation[0];
  }
  if (/(code|kotlin|javascript|sql|worker|algorithme)/.test(lower)) {
    return INTENT_PRIORITY_CHAINS.coding[0];
  }
  return INTENT_PRIORITY_CHAINS.chat[0];
}

function buildFallbackOrder(primaryModel, intent = "chat") {
  const intentChain = INTENT_PRIORITY_CHAINS[intent] || [];
  return [...new Set([primaryModel, ...intentChain, ...DEFAULT_AI_FALLBACK_CHAIN].filter((m) => ALLOWED_AI_MODEL_IDS.has(m)))];
}

function corsHeaders(request) {
  const origin = request.headers.get("Origin") || "*";
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, Range, X-Request-Id, X-Brav-Bucket",
    "Access-Control-Expose-Headers": "Content-Range, Accept-Ranges, Content-Length, ETag, X-Request-Id",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin"
  };
}

function jsonResponse(request, data, status = 200, requestId = crypto.randomUUID()) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders(request),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "X-Request-Id": requestId
    }
  });
}

function base64UrlToUint8Array(str) {
  const pad = "=".repeat((4 - (str.length % 4)) % 4);
  const b64 = (str + pad).replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function decodeJwtPart(part) {
  const bytes = base64UrlToUint8Array(part);
  return JSON.parse(new TextDecoder().decode(bytes));
}

async function fetchFirebaseJwks() {
  const now = Date.now();
  if (cachedJwks && now < cachedJwksExpiresAt) return cachedJwks;
  const res = await fetch(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
  );
  if (!res.ok) throw new Error("JWKS_FETCH_FAILED");
  const jwks = await res.json();
  cachedJwks = jwks.keys || [];
  cachedJwksExpiresAt = now + 3600_000;
  return cachedJwks;
}

function extractBearerToken(request) {
  const auth = request.headers.get("Authorization") || "";
  if (auth.startsWith("Bearer ")) return auth.slice(7).trim();
  const proto = request.headers.get("Sec-WebSocket-Protocol") || "";
  for (const part of proto.split(",")) {
    const trimmed = part.trim();
    if (trimmed.startsWith("bearer.")) return trimmed.slice(7).trim();
  }
  return "";
}

async function verifyFirebaseAuth(request, env) {
  const token = extractBearerToken(request);
  if (!token) return null;

  if (typeof env.__TEST_AUTH_VERIFIER === "function") {
    return env.__TEST_AUTH_VERIFIER(token);
  }

  const parts = token.split(".");
  if (parts.length !== 3) return null;

  try {
    const header = decodeJwtPart(parts[0]);
    const payload = decodeJwtPart(parts[1]);
    const projectId = env.FIREBASE_PROJECT_ID || "brav-chat";
    const nowSec = Math.floor(Date.now() / 1000);

    if (header.alg !== "RS256" || !header.kid) return null;
    if (payload.aud !== projectId) return null;
    if (payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
    if (!payload.sub || typeof payload.sub !== "string") return null;
    if (typeof payload.exp !== "number" || payload.exp <= nowSec) return null;

    const keys = await fetchFirebaseJwks();
    const jwk = keys.find((k) => k.kid === header.kid);
    if (!jwk) return null;

    const cryptoKey = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );
    const sig = base64UrlToUint8Array(parts[2]);
    const data = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey, sig, data);
    if (!valid) return null;

    return {
      uid: payload.sub,
      email: payload.email || "",
      emailVerified: Boolean(payload.email_verified)
    };
  } catch {
    return null;
  }
}

const DESIGNER_EXEMPT_EMAIL = "bravoboutique225@gmail.com";
const CERT_REQUIRED_FOLLOWERS = 5000;
const CERT_FEE_XOF = 3600;
const CERT_FEE_USD_CENTS = 599;
const CERT_ADMIN_EMAILS = new Set(["comptbravo78@gmail.com", "admin@bravchat.ci"]);

export function isDesignerExempt(user) {
  return Boolean(
    user &&
    user.emailVerified === true &&
    String(user.email || "").trim().toLowerCase() === DESIGNER_EXEMPT_EMAIL
  );
}

export function isAdminUser(user, env) {
  if (!user || user.emailVerified !== true) return false;
  const email = String(user.email || "").trim().toLowerCase();
  if (CERT_ADMIN_EMAILS.has(email)) return true;
  const extra = String(env?.ADMIN_EMAILS || "")
    .split(",")
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean);
  return extra.includes(email);
}

async function getFollowerCount(env, targetUid) {
  if (env.DB) {
    const row = await env.DB.prepare(
      "SELECT COUNT(*) AS cnt FROM user_follows WHERE target_uid = ?"
    ).bind(targetUid).first();
    if (row && typeof row.cnt === "number") return Number(row.cnt);
  }
  const kvCount = await kvGetJson(env, `follows:count:${targetUid}`);
  return Number(kvCount?.count || 0);
}

async function setKvFollowerCount(env, targetUid, count) {
  await kvPutJson(env, `follows:count:${targetUid}`, { count: Math.max(0, Number(count || 0)) }, 86400 * 30);
}

function extFromMime(mime, fallbackName = "") {
  const fromName = fallbackName.includes(".") ? fallbackName.split(".").pop().toLowerCase().replace(/[^a-z0-9]/g, "") : "";
  if (fromName) return fromName.slice(0, 8);
  const map = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
    "image/gif": "gif",
    "audio/mp4": "m4a",
    "audio/aac": "aac",
    "audio/mpeg": "mp3",
    "audio/ogg": "ogg",
    "audio/webm": "webm",
    "video/mp4": "mp4",
    "video/webm": "webm",
    "application/pdf": "pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx"
  };
  return map[mime] || "bin";
}

async function pushToUserHub(env, targetUid, payloadObj) {
  if (!env.USER_HUBS || !targetUid) return 0;
  try {
    const stub = env.USER_HUBS.get(env.USER_HUBS.idFromName(String(targetUid)));
    const res = await stub.fetch(new Request("https://user-hub.internal/internal/push", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payloadObj)
    }));
    if (res.ok) {
      const data = await res.json().catch(() => ({}));
      return Number(data.delivered || 0);
    }
  } catch {}
  return 0;
}

async function enqueueTask(env, taskPayload) {
  if (!env.TASKS_QUEUE || typeof env.TASKS_QUEUE.send !== "function") return false;
  try {
    await env.TASKS_QUEUE.send({ ...taskPayload, enqueuedAt: Date.now() });
    return true;
  } catch {
    return false;
  }
}

const VALID_NOTIFICATION_CATEGORIES = new Set([
  "messages",
  "contacts",
  "actus",
  "calls",
  "offers_jobs",
  "account_security"
]);

async function emitWorkerNotification(env, {
  targetUid,
  senderUid = "",
  senderName = "",
  senderAvatar = "",
  category = "messages",
  type = "message",
  title = "BRAV Chat",
  body = "",
  refId = "",
  refType = "",
  subRefId = "",
  dedupKey = "",
  priority = "normal"
}) {
  if (!targetUid || !title || !body) return null;
  const safeCat = VALID_NOTIFICATION_CATEGORIES.has(category) ? category : "messages";
  const now = Date.now();
  const notif = {
    id: `ntf_${crypto.randomUUID().replace(/-/g, "").slice(0, 18)}`,
    userId: String(targetUid).slice(0, 128),
    senderUid: String(senderUid || "").slice(0, 128),
    senderName: String(senderName || "").slice(0, 120),
    senderAvatar: String(senderAvatar || "").slice(0, 1000),
    category: safeCat,
    type: String(type || "event").slice(0, 64),
    title: String(title).slice(0, 160),
    body: String(body).slice(0, 500),
    refId: String(refId || "").slice(0, 160),
    refType: String(refType || "").slice(0, 64),
    subRefId: String(subRefId || "").slice(0, 160),
    dedupKey: String(dedupKey || "").slice(0, 180),
    priority: priority === "high" || priority === "critical" ? priority : "normal",
    isRead: false,
    readAtMs: 0,
    createdAtMs: now
  };

  if (env.DB) {
    if (notif.dedupKey) {
      const existing = await env.DB.prepare(
        "SELECT id FROM notifications WHERE user_id = ? AND dedup_key = ? LIMIT 1"
      ).bind(notif.userId, notif.dedupKey).first().catch(() => null);
      if (existing) {
        return { duplicate: true, id: existing.id };
      }
    }
    await env.DB.prepare(
      "INSERT INTO notifications (id, user_id, sender_uid, sender_name, sender_avatar, category, type, title, body, ref_id, ref_type, sub_ref_id, dedup_key, priority, is_read, read_at_ms, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?)"
    ).bind(
      notif.id, notif.userId, notif.senderUid, notif.senderName, notif.senderAvatar,
      notif.category, notif.type, notif.title, notif.body,
      notif.refId, notif.refType, notif.subRefId, notif.dedupKey, notif.priority, notif.createdAtMs
    ).run();
  } else {
    const kvKey = `notifs:${notif.userId}`;
    const existingList = (await kvGetJson(env, kvKey))?.items || [];
    if (notif.dedupKey && existingList.some((n) => n.dedupKey === notif.dedupKey)) {
      return { duplicate: true };
    }
    const nextList = [notif, ...existingList].slice(0, 120);
    await kvPutJson(env, kvKey, { items: nextList }, 86400 * 30);
  }

  await pushToUserHub(env, notif.userId, { type: "notification", notification: notif });
  return { duplicate: false, notification: notif };
}

async function kvGetJson(env, key) {
  if (!env.KV) return null;
  try {
    const raw = await env.KV.get(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

async function kvPutJson(env, key, value, ttlSeconds = 3600) {
  if (!env.KV) return;
  try {
    await env.KV.put(key, JSON.stringify(value), { expirationTtl: ttlSeconds });
  } catch {}
}

async function checkAndReserveAiQuota(env, uid) {
  const dailyLimit = Math.max(20, Number(env.AI_DAILY_USER_QUOTA || "250"));
  const dayKey = new Date().toISOString().slice(0, 10);
  const kvKey = `ai:quota:${uid}:${dayKey}`;
  const existing = await kvGetJson(env, kvKey);
  const currentCount = Number(existing?.count || 0);
  if (currentCount >= dailyLimit) {
    return { allowed: false, count: currentCount, limit: dailyLimit, dayKey };
  }
  await kvPutJson(env, kvKey, { count: currentCount + 1 }, 90_000);
  return { allowed: true, count: currentCount + 1, limit: dailyLimit, dayKey };
}

async function isModelCircuitOpen(env, modelId) {
  const cb = await kvGetJson(env, `ai:cb:${modelId}`);
  return Boolean(cb && cb.openUntilMs && Date.now() < cb.openUntilMs);
}

async function markModelFailure(env, modelId) {
  await kvPutJson(env, `ai:cb:${modelId}`, { openUntilMs: Date.now() + 90_000 }, 120);
}

const STRICT_FRENCH_SYSTEM_DIRECTIVE =
  "RÈGLE ABSOLUE ET IMPÉRATIVE : Tu dois OBLIGATOIREMENT répondre uniquement en français. Il t'est formellement interdit de répondre en anglais ou d'écrire des phrases en anglais, même si l'utilisateur emploie des mots étrangers ou techniques.";

const MATH_PEDAGOGY_DIRECTIVE =
  "DIRECTIVE PÉDAGOGIQUE MATHÉMATIQUE : " +
  "1) Explique toujours comme un professeur patient et clair, étape par étape : Compréhension/Énoncé, Données connues, Méthode et formule expliquée, Calcul détaillé ligne par ligne, Résultat final mis en évidence et Conclusion. " +
  "2) N'affiche JAMAIS de commandes LaTeX brutes hors délimiteurs ni de code informatique à la place d'une explication : encadre TOUJOURS les expressions inline avec $...$ et les formules ou matrices avec $$...$$ pour qu'elles soient rendues visuellement (fractions superposées, racines, vecteurs, matrices, intégrales). " +
  "3) Pour les vecteurs (ex. addition), liste d'abord les vecteurs et leurs coordonnées (ex. Vecteur u : (x1 ; y1 ; z1)), puis additionne coordonnée par coordonnée avant de conclure clairement.";

export function stripAiReasoningTags(rawText) {
  return String(rawText || "")
    .replace(/<think>[\s\S]*?<\/think>/gi, "")
    .replace(/<think>[\s\S]*$/gi, "")
    .replace(/<\/think>/gi, "")
    .trim();
}

export function looksPredominantlyEnglish(rawText) {
  const withoutMath = String(rawText || "")
    .replace(/\$\$[\s\S]*?\$\$/g, " ")
    .replace(/\$[^$\n]+\$/g, " ")
    .replace(/```[\s\S]*?```/g, " ")
    .toLowerCase();
  const enMatches = withoutMath.match(
    /\b(the|and|that|with|this|you|your|is|are|was|were|have|has|will|would|could|should|here|answer|solution|please|hello|hi|thank|thanks|sorry|welcome|sure|therefore|however|because|below|above|following|using|calculate|step)\b/g
  ) || [];
  const frMatches = withoutMath.match(
    /([éèêëàâäîïôöùûüç]|\b(le|la|les|des|une|est|sont|pour|avec|dans|sur|par|pas|nous|vous|ils|elles|cette|ceci|cela|donc|car|mais|bonjour|merci|voici|étape|résultat|réponse|calcul|donne|soit)\b)/g
  ) || [];
  if (enMatches.length >= 3 && frMatches.length === 0) return true;
  if (enMatches.length >= 4 && enMatches.length >= frMatches.length * 2) return true;
  return false;
}

export async function runCloudflareAiWithFallback(env, preferredModelId, messages, maxTokens = 1024, intent = "chat") {
  const candidates = buildFallbackOrder(preferredModelId, intent);
  const gatewayOpts = env.AI_GATEWAY_ID ? { gateway: { id: String(env.AI_GATEWAY_ID) } } : undefined;
  let attempts = 0;

  for (const candidate of candidates) {
    if (await isModelCircuitOpen(env, candidate)) continue;
    attempts++;
    try {
      const aiOut = gatewayOpts
        ? await env.AI.run(candidate, { messages, max_tokens: maxTokens }, gatewayOpts)
        : await env.AI.run(candidate, { messages, max_tokens: maxTokens });
      const raw = stripAiReasoningTags(aiOut?.response || aiOut?.result?.response || "");
      if (raw) {
        return { model: candidate, text: raw, attempts, gatewayUsed: Boolean(gatewayOpts) };
      }
    } catch {
      await markModelFailure(env, candidate);
    }
  }

  // Last chance: retry primary ignoring circuit breaker if all circuit breakers were tripped
  for (const candidate of candidates.slice(0, 2)) {
    try {
      const aiOut = gatewayOpts
        ? await env.AI.run(candidate, { messages, max_tokens: maxTokens }, gatewayOpts)
        : await env.AI.run(candidate, { messages, max_tokens: maxTokens });
      const raw = stripAiReasoningTags(aiOut?.response || aiOut?.result?.response || "");
      if (raw) {
        return { model: candidate, text: raw, attempts: attempts + 1, gatewayUsed: Boolean(gatewayOpts) };
      }
    } catch {}
  }

  return { model: preferredModelId, text: "", attempts, gatewayUsed: Boolean(gatewayOpts) };
}

export async function enforceFrenchOutput(env, text, intent = "chat") {
  const cleaned = stripAiReasoningTags(text);
  if (!cleaned || intent === "translation") return cleaned;
  if (!looksPredominantlyEnglish(cleaned) || !env.AI) return cleaned;

  const tr = await runCloudflareAiWithFallback(
    env,
    "@cf/meta/llama-3.1-8b-instruct",
    [
      {
        role: "system",
        content:
          "Tu es un traducteur français strict. Traduis le texte suivant intégralement en français naturel. Conserve intactes toutes les expressions LaTeX ($...$ et $$...$$), les puces et les émojis. Ne réponds jamais en anglais."
      },
      { role: "user", content: cleaned }
    ],
    1400,
    "translation"
  );
  return tr.text ? stripAiReasoningTags(tr.text) : cleaned;
}

async function sha256Hex(input) {
  const bytes = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

export default {
  async fetch(request, env, ctx) {
    const requestId = crypto.randomUUID();
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(request) });
    }

    const url = new URL(request.url);
    const path = url.pathname;
    const baseUrl = `${url.protocol}//${url.host}`;
    const wsBaseUrl = baseUrl.replace(/^http/, "ws");
    const sfuAppId = env.SFU_APP_ID || "00e84148168d52e644ef50bc3824ff3b";

    // Public configuration endpoint (zero secrets exposed)
    if (request.method === "GET" && path === "/v1/config") {
      const maxUploadMb = Number(env.MAX_UPLOAD_MB || "25");
      return jsonResponse(request, {
        version: "4.0.0",
        baseUrl,
        realtimeUrl: `${wsBaseUrl}/v1/realtime`,
        callSignalUrlTemplate: `${wsBaseUrl}/v1/calls/{callId}/signal`,
        websocketSubprotocol: WS_SUBPROTOCOL,
        sfuAppId,
        r2Bucket: env.R2_BUCKET_NAME || "bravchat",
        d1Database: env.D1_DATABASE_NAME || "brav_chat",
        aiGatewayEnabled: Boolean(env.AI_GATEWAY_ID),
        heartbeat: { clientSends: "ping", serverReplies: "pong", everyMs: 25000 },
        limits: { signalBytes: 32768, uploadMB: maxUploadMb, presenceUidsPerQuery: 100, ringTimeoutMs: 45000 },
        mediaTypes: ALLOWED_MEDIA_TYPES,
        features: {
          auth: true,
          realtimeHub: Boolean(env.USER_HUBS),
          callSignaling: Boolean(env.CALL_ROOMS),
          turn: env.TURN_TOKEN_ID && env.TURN_API_TOKEN ? "cloudflare" : "stun",
          sfu: Boolean(sfuAppId && env.SFU_API_TOKEN),
          media: Boolean(env.R2),
          kv: Boolean(env.KV),
          queues: Boolean(env.TASKS_QUEUE),
          push: Boolean(env.DB),
          presenceSync: Boolean(env.USER_HUBS),
          missedCalls: Boolean(env.USER_HUBS),
          search: Boolean(env.DB),
          ai: Boolean(env.AI),
          aiGateway: Boolean(env.AI_GATEWAY_ID),
          latexMath: true,
          documentGeneration: Boolean(env.AI),
          payments: true,
          paymentFinalization: true,
          certificationFeeXof: CERT_FEE_XOF,
          certificationFeeUsdCents: CERT_FEE_USD_CENTS,
          certificationMinFollowers: CERT_REQUIRED_FOLLOWERS
        },
        aiModels: FREE_AI_MODELS
      }, 200, requestId);
    }

    const user = await verifyFirebaseAuth(request, env);
    if (!user) {
      return jsonResponse(request, { error: "UNAUTHORIZED", requestId }, 401, requestId);
    }

    if (request.method === "GET" && path === "/v1/whoami") {
      return jsonResponse(request, { ok: true, uid: user.uid, email: user.email }, 200, requestId);
    }

    // =========================================================================
    // CLOUDFLARE R2 MEDIA STORAGE (bucket: bravchat)
    // =========================================================================
    if (path === "/v1/media" && request.method === "POST") {
      if (!env.R2) {
        return jsonResponse(request, { error: "MEDIA_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const conversationId = (url.searchParams.get("conversationId") || `user_${user.uid}`).replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 128);
      const fileName = (url.searchParams.get("fileName") || "").slice(0, 128);
      const contentType = (request.headers.get("Content-Type") || "application/octet-stream").split(";")[0].trim().toLowerCase();
      const maxBytes = Number(env.MAX_UPLOAD_MB || "25") * 1024 * 1024;
      const bodyBytes = await request.arrayBuffer();

      if (!bodyBytes || bodyBytes.byteLength === 0) {
        return jsonResponse(request, { error: "EMPTY_FILE", requestId }, 400, requestId);
      }
      if (bodyBytes.byteLength > maxBytes) {
        return jsonResponse(request, { error: "FILE_TOO_LARGE", requestId }, 413, requestId);
      }

      const ext = extFromMime(contentType, fileName);
      const objectKey = `c/${conversationId}/${crypto.randomUUID()}.${ext}`;
      await env.R2.put(objectKey, bodyBytes, {
        httpMetadata: {
          contentType,
          cacheControl: "private, max-age=86400"
        },
        customMetadata: {
          ownerUid: user.uid,
          conversationId,
          fileName
        }
      });

      if (env.DB) {
        await env.DB.prepare(
          "INSERT OR REPLACE INTO media_objects (object_key, owner_uid, conversation_id, file_name, content_type, size_bytes, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).bind(objectKey, user.uid, conversationId, fileName, contentType, bodyBytes.byteLength, Date.now()).run();
      }

      return jsonResponse(request, {
        ok: true,
        key: objectKey,
        url: `${baseUrl}/v1/media/${objectKey}`,
        size: bodyBytes.byteLength,
        contentType
      }, 201, requestId);
    }

    if (path.startsWith("/v1/media/") && (request.method === "GET" || request.method === "HEAD")) {
      if (!env.R2) {
        return jsonResponse(request, { error: "MEDIA_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const objectKey = decodeURIComponent(path.slice("/v1/media/".length));
      if (!objectKey) {
        return jsonResponse(request, { error: "NOT_FOUND", requestId }, 404, requestId);
      }
      if (objectKey.startsWith("private/")) {
        const head = await env.R2.head(objectKey);
        if (!head) {
          return jsonResponse(request, { error: "NOT_FOUND", requestId }, 404, requestId);
        }
        const ownerUid = head.customMetadata?.ownerUid || "";
        if (ownerUid !== user.uid && !isAdminUser(user, env)) {
          return jsonResponse(request, { error: "FORBIDDEN_PRIVATE_MEDIA", requestId }, 403, requestId);
        }
      }
      const rangeHeader = request.headers.get("Range");
      const r2Obj = rangeHeader
        ? await env.R2.get(objectKey, { range: request.headers })
        : await env.R2.get(objectKey);
      if (!r2Obj) {
        return jsonResponse(request, { error: "NOT_FOUND", requestId }, 404, requestId);
      }
      if (objectKey.startsWith("private/")) {
        const ownerUid = r2Obj.customMetadata?.ownerUid || "";
        if (ownerUid !== user.uid && !isAdminUser(user, env)) {
          return jsonResponse(request, { error: "FORBIDDEN_PRIVATE_MEDIA", requestId }, 403, requestId);
        }
      }
      const headers = new Headers(corsHeaders(request));
      r2Obj.writeHttpMetadata(headers);
      headers.set("ETag", r2Obj.httpEtag);
      headers.set("Accept-Ranges", "bytes");
      headers.set("X-Content-Type-Options", "nosniff");
      if (objectKey.startsWith("private/")) {
        headers.set("Cache-Control", "no-store, private");
      }
      if (r2Obj.range && "offset" in r2Obj.range) {
        const start = r2Obj.range.offset;
        const end = start + r2Obj.range.length - 1;
        headers.set("Content-Range", `bytes ${start}-${end}/${r2Obj.size}`);
        headers.set("Content-Length", String(r2Obj.range.length));
        return new Response(request.method === "HEAD" ? null : r2Obj.body, { status: 206, headers });
      }
      headers.set("Content-Length", String(r2Obj.size));
      return new Response(request.method === "HEAD" ? null : r2Obj.body, { status: 200, headers });
    }

    if (path.startsWith("/v1/media/") && request.method === "DELETE") {
      if (!env.R2) {
        return jsonResponse(request, { error: "MEDIA_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const objectKey = decodeURIComponent(path.slice("/v1/media/".length));
      const head = await env.R2.head(objectKey);
      if (!head) return jsonResponse(request, { ok: true }, 200, requestId);
      if (head.customMetadata?.ownerUid && head.customMetadata.ownerUid !== user.uid) {
        return jsonResponse(request, { error: "FORBIDDEN", requestId }, 403, requestId);
      }
      await env.R2.delete(objectKey);
      if (env.DB) {
        await env.DB.prepare("DELETE FROM media_objects WHERE object_key = ?").bind(objectKey).run();
      }
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    // =========================================================================
    // CLOUDFLARE TURN & REALTIME SFU
    // =========================================================================
    if (request.method === "GET" && path === "/v1/turn-credentials") {
      const ttl = Math.min(86400, Math.max(60, Number(url.searchParams.get("ttl") || "14400")));
      if (env.TURN_TOKEN_ID && env.TURN_API_TOKEN) {
        const res = await fetch(
          `https://rtc.live.cloudflare.com/v1/turn/keys/${env.TURN_TOKEN_ID}/credentials/generate-ice-servers`,
          {
            method: "POST",
            headers: {
              "Authorization": `Bearer ${env.TURN_API_TOKEN}`,
              "Content-Type": "application/json"
            },
            body: JSON.stringify({ ttl })
          }
        );
        if (res.ok) {
          const data = await res.json();
          return jsonResponse(request, {
            provider: "cloudflare",
            relay: true,
            iceServers: data.iceServers || [],
            expiresAt: Date.now() + ttl * 1000
          }, 200, requestId);
        }
      }
      return jsonResponse(request, {
        provider: "stun-only",
        relay: false,
        iceServers: [{ urls: ["stun:stun.cloudflare.com:3478", "stun:stun.l.google.com:19302"] }],
        expiresAt: Date.now() + ttl * 1000
      }, 200, requestId);
    }

    const sfuSessionMatch = path.match(/^\/v1\/sfu\/calls\/([a-zA-Z0-9_-]+)\/sessions$/);
    if (sfuSessionMatch && request.method === "POST") {
      if (!env.SFU_API_TOKEN) {
        return jsonResponse(request, { error: "SFU_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const callId = sfuSessionMatch[1];
      const payload = await request.json();
      const cfRes = await fetch(`https://rtc.live.cloudflare.com/v1/apps/${sfuAppId}/sessions/new`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.SFU_API_TOKEN}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ sessionDescription: payload.sessionDescription })
      });
      const cfJson = await cfRes.json();
      if (!cfRes.ok) {
        return jsonResponse(request, { error: "SFU_SESSION_ERROR", details: cfJson, requestId }, cfRes.status, requestId);
      }
      if (env.DB && cfJson.sessionId) {
        await env.DB.prepare(
          "INSERT OR REPLACE INTO call_sfu_sessions (call_id, session_id, user_id, tracks_json, updated_at_ms) VALUES (?, ?, ?, '[]', ?)"
        ).bind(callId, cfJson.sessionId, user.uid, Date.now()).run();
      }
      return jsonResponse(request, cfJson, 200, requestId);
    }

    const sfuTracksMatch = path.match(/^\/v1\/sfu\/calls\/([a-zA-Z0-9_-]+)\/sessions\/([a-zA-Z0-9_-]+)\/tracks$/);
    if (sfuTracksMatch && request.method === "POST") {
      if (!env.SFU_API_TOKEN) {
        return jsonResponse(request, { error: "SFU_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const [, callId, sessionId] = sfuTracksMatch;
      const payload = await request.json();
      const cfRes = await fetch(`https://rtc.live.cloudflare.com/v1/apps/${sfuAppId}/sessions/${sessionId}/tracks/new`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.SFU_API_TOKEN}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });
      const cfJson = await cfRes.json();
      if (!cfRes.ok) {
        return jsonResponse(request, { error: "SFU_TRACKS_ERROR", details: cfJson, requestId }, cfRes.status, requestId);
      }
      if (env.DB && Array.isArray(payload.tracks)) {
        const localTracks = payload.tracks.filter((t) => t.location === "local");
        if (localTracks.length > 0) {
          await env.DB.prepare(
            "INSERT OR REPLACE INTO call_sfu_sessions (call_id, session_id, user_id, tracks_json, updated_at_ms) VALUES (?, ?, ?, ?, ?)"
          ).bind(callId, sessionId, user.uid, JSON.stringify(localTracks), Date.now()).run();
        }
      }
      return jsonResponse(request, cfJson, 200, requestId);
    }

    const sfuRenegMatch = path.match(/^\/v1\/sfu\/calls\/([a-zA-Z0-9_-]+)\/sessions\/([a-zA-Z0-9_-]+)\/renegotiate$/);
    if (sfuRenegMatch && request.method === "PUT") {
      if (!env.SFU_API_TOKEN) {
        return jsonResponse(request, { error: "SFU_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const [, , sessionId] = sfuRenegMatch;
      const payload = await request.json();
      const cfRes = await fetch(`https://rtc.live.cloudflare.com/v1/apps/${sfuAppId}/sessions/${sessionId}/renegotiate`, {
        method: "PUT",
        headers: {
          "Authorization": `Bearer ${env.SFU_API_TOKEN}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });
      const cfJson = await cfRes.json();
      return jsonResponse(request, cfJson, cfRes.status, requestId);
    }

    const sfuListTracksMatch = path.match(/^\/v1\/sfu\/calls\/([a-zA-Z0-9_-]+)\/tracks$/);
    if (sfuListTracksMatch && request.method === "GET") {
      if (!env.DB) return jsonResponse(request, { tracks: [] }, 200, requestId);
      const callId = sfuListTracksMatch[1];
      const rows = await env.DB.prepare(
        "SELECT session_id, user_id, tracks_json FROM call_sfu_sessions WHERE call_id = ? AND user_id != ?"
      ).bind(callId, user.uid).all();
      const tracks = [];
      for (const row of (rows.results || [])) {
        try {
          const parsed = JSON.parse(row.tracks_json || "[]");
          for (const t of parsed) {
            tracks.push({ location: "remote", sessionId: row.session_id, trackName: t.trackName });
          }
        } catch {}
      }
      return jsonResponse(request, { tracks }, 200, requestId);
    }

    // =========================================================================
    // REALTIME HUB + CALL SIGNALING DURABLE OBJECTS
    // =========================================================================
    if (path === "/v1/realtime") {
      if (!env.USER_HUBS) {
        return jsonResponse(request, { error: "USER_HUBS_BINDING_MISSING", requestId }, 503, requestId);
      }
      const deviceId = (url.searchParams.get("deviceId") || "android").slice(0, 64);
      const stub = env.USER_HUBS.get(env.USER_HUBS.idFromName(user.uid));
      const headers = new Headers(request.headers);
      headers.set("X-Brav-Uid", user.uid);
      headers.set("X-Brav-Device", deviceId);
      const fwd = new Request(request.url, { method: request.method, headers });
      return stub.fetch(fwd);
    }

    const callSignalMatch = path.match(/^\/v1\/calls\/([a-zA-Z0-9_-]+)\/signal$/);
    if (callSignalMatch) {
      if (!env.CALL_ROOMS) {
        return jsonResponse(request, { error: "CALL_ROOMS_BINDING_MISSING", requestId }, 503, requestId);
      }
      const stub = env.CALL_ROOMS.get(env.CALL_ROOMS.idFromName(callSignalMatch[1]));
      const headers = new Headers(request.headers);
      headers.set("X-Brav-Uid", user.uid);
      const fwd = new Request(request.url, { method: request.method, headers });
      return stub.fetch(fwd);
    }

    const callRingMatch = path.match(/^\/v1\/calls\/([a-zA-Z0-9_-]+)\/ring$/);
    if (callRingMatch && request.method === "POST") {
      const callId = callRingMatch[1];
      let body = {};
      try { body = await request.json(); } catch {}
      if (env.DB && body.receiverId) {
        const blk = await env.DB.prepare(
          "SELECT 1 FROM blocked_users WHERE (blocker_uid = ? AND blocked_uid = ?) OR (blocker_uid = ? AND blocked_uid = ?) LIMIT 1"
        ).bind(String(body.receiverId), user.uid, user.uid, String(body.receiverId)).first();
        if (blk) {
          return jsonResponse(request, { error: "USER_BLOCKED", requestId }, 403, requestId);
        }
      }
      let delivered = 0;
      if (body.receiverId) {
        const callEvent = {
          type: "incoming_call",
          callId,
          callType: body.callType || "audio",
          callerId: user.uid,
          callerName: body.callerName || "",
          sentAt: Date.now()
        };
        delivered = await pushToUserHub(env, String(body.receiverId), callEvent);
        if (delivered === 0) {
          await enqueueTask(env, { type: "push_missed_fallback", targetUid: String(body.receiverId), event: callEvent });
        }
      }
      return jsonResponse(request, { ok: true, callId, delivered }, 200, requestId);
    }

    // =========================================================================
    // D1 DATABASE SYNC & SEARCH (brav_chat)
    // =========================================================================
    if (path === "/v1/users/sync" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const b = await request.json();
      await env.DB.prepare(
        "INSERT OR REPLACE INTO users_index (user_id, display_name, username, location_city, avatar_url, online, last_seen_ms, updated_at_ms) VALUES (?, ?, ?, ?, ?, 1, ?, ?)"
      ).bind(
        user.uid,
        String(b.displayName || "").slice(0, 100),
        String(b.username || "").toLowerCase().slice(0, 40),
        String(b.locationCity || "").slice(0, 120),
        String(b.avatarUrl || "").slice(0, 1000),
        Date.now(),
        Date.now()
      ).run();
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/offers/sync" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const b = await request.json();
      await env.DB.prepare(
        "INSERT OR REPLACE INTO offers_index (id, user_id, title, category, description, location_city, price_or_salary, author_name, status, payment_status, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'published', 'free', ?)"
      ).bind(
        String(b.id || crypto.randomUUID()),
        user.uid,
        String(b.title || "").slice(0, 160),
        String(b.category || "Services").slice(0, 80),
        String(b.description || "").slice(0, 2000),
        String(b.locationCity || "").slice(0, 120),
        String(b.priceOrSalary || "Gratuit").slice(0, 80),
        String(b.authorName || "").slice(0, 120),
        Date.now()
      ).run();
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/search" && request.method === "GET") {
      if (!env.DB) return jsonResponse(request, { users: [], offers: [] }, 200, requestId);
      const q = (url.searchParams.get("q") || "").trim();
      const type = url.searchParams.get("type") || "all";
      const limit = Math.min(50, Math.max(1, Number(url.searchParams.get("limit") || "20")));
      const like = `%${q.replace(/^@/, "")}%`;
      let users = [];
      let offers = [];
      if (type === "all" || type === "users") {
        const res = await env.DB.prepare(
          "SELECT user_id AS userId, display_name AS displayName, username, location_city AS locationCity, avatar_url AS avatarUrl FROM users_index WHERE user_id != ? AND (display_name LIKE ? OR username LIKE ? OR location_city LIKE ?) LIMIT ?"
        ).bind(user.uid, like, like, like, limit).all();
        users = res.results || [];
      }
      if (type === "all" || type === "offers") {
        const res = await env.DB.prepare(
          "SELECT id, user_id AS userId, title, category, description, location_city AS locationCity, price_or_salary AS priceOrSalary, author_name AS authorName, status, payment_status AS paymentStatus FROM offers_index WHERE status = 'published' AND (title LIKE ? OR description LIKE ? OR location_city LIKE ?) ORDER BY created_at_ms DESC LIMIT ?"
        ).bind(like, like, like, limit).all();
        offers = res.results || [];
      }
      return jsonResponse(request, { users, offers }, 200, requestId);
    }

    if (path === "/v1/devices" && request.method === "POST") {
      if (env.DB) {
        const b = await request.json();
        await env.DB.prepare(
          "INSERT OR REPLACE INTO device_tokens (token, user_id, platform, updated_at_ms) VALUES (?, ?, ?, ?)"
        ).bind(String(b.token || ""), user.uid, String(b.platform || "android"), Date.now()).run();
      }
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/devices" && request.method === "DELETE") {
      if (env.DB) {
        const b = await request.json();
        await env.DB.prepare("DELETE FROM device_tokens WHERE token = ? AND user_id = ?").bind(String(b.token || ""), user.uid).run();
      }
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/presence" && request.method === "GET") {
      const raw = (url.searchParams.get("uids") || "").split(",").map((s) => s.trim()).filter(Boolean).slice(0, 100);
      if (!env.DB || raw.length === 0) return jsonResponse(request, { users: [] }, 200, requestId);
      const placeholders = raw.map(() => "?").join(",");
      const res = await env.DB.prepare(
        `SELECT user_id AS uid, online, last_seen_ms AS lastSeen FROM users_index WHERE user_id IN (${placeholders})`
      ).bind(...raw).all();
      return jsonResponse(request, {
        users: (res.results || []).map((r) => ({ uid: r.uid, online: Boolean(r.online), lastSeen: r.lastSeen || 0 }))
      }, 200, requestId);
    }

    if (path === "/v1/chat/notify" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const conversationId = String(b.conversationId || "").slice(0, 128);
      const messageId = String(b.messageId || "").slice(0, 128);
      const senderName = String(b.senderName || "").slice(0, 100);
      const messageType = String(b.messageType || "text").slice(0, 32);
      const preview = String(b.preview || "").slice(0, 160);
      const recipients = Array.isArray(b.recipientIds)
        ? [...new Set(b.recipientIds.map((r) => String(r || "").trim()).filter((r) => r && r !== user.uid))].slice(0, 50)
        : [];

      const event = {
        type: "message",
        conversationId,
        messageId,
        senderId: user.uid,
        senderName,
        messageType,
        preview,
        sentAt: Date.now()
      };

      let delivered = 0;
      if (env.USER_HUBS && recipients.length > 0) {
        const counts = await Promise.all(recipients.map((uid) => pushToUserHub(env, uid, event)));
        delivered = counts.reduce((sum, n) => sum + n, 0);
      }
      if (recipients.length > 0 && delivered < recipients.length) {
        await enqueueTask(env, { type: "chat_notify_fanout", recipients, event });
      }
      return jsonResponse(request, { ok: true, delivered }, 200, requestId);
    }

    // =========================================================================
    // CLOUDFLARE WORKERS AI + AI GATEWAY + KV QUOTAS + AUTO-ROUTING
    // =========================================================================
    if (path === "/v1/ai/models" && request.method === "GET") {
      return jsonResponse(request, {
        enabled: Boolean(env.AI),
        gateway: env.AI_GATEWAY_ID || "",
        models: FREE_AI_MODELS
      }, 200, requestId);
    }

    if (path === "/v1/ai/chat" && request.method === "POST") {
      if (!env.AI) {
        return jsonResponse(request, { error: "AI_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const quota = await checkAndReserveAiQuota(env, user.uid);
      if (!quota.allowed) {
        return jsonResponse(request, { error: "QUOTA_EXCEEDED", quota, requestId }, 429, requestId);
      }

      let b = {};
      try { b = await request.json(); } catch {}
      const prompt = String(b.prompt || "").trim();
      if (!prompt || prompt.length > 6000) {
        return jsonResponse(request, { error: "INVALID_PROMPT", requestId }, 400, requestId);
      }
      const intent = String(b.intent || "").trim().toLowerCase() || "chat";
      const modelId = selectModelForIntent(intent, prompt, b.model);
      const customSys = String(b.systemPrompt || "").trim();
      const defaultSys =
        `Tu es l'assistant intelligent intégré à BRAV Chat, propulsé à 100% par Cloudflare Workers AI. ${MATH_PEDAGOGY_DIRECTIVE}`;
      const mathAddon = intent === "math" || intent === "latex_math" ? ` ${MATH_PEDAGOGY_DIRECTIVE}` : "";
      const systemPrompt = `${STRICT_FRENCH_SYSTEM_DIRECTIVE} ${customSys || defaultSys}${mathAddon}`.slice(0, 2600);

      const messages = [{ role: "system", content: systemPrompt }];
      if (Array.isArray(b.history)) {
        for (const h of b.history.slice(-8)) {
          const role = h?.role === "assistant" ? "assistant" : "user";
          const content = String(h?.content || "").trim().slice(0, 1800);
          if (content) messages.push({ role, content });
        }
      }
      messages.push({ role: "user", content: prompt });

      const noHistory = !Array.isArray(b.history) || b.history.length === 0;
      const cacheKey = await sha256Hex(`chat:v6:fr:${modelId}:${systemPrompt}:${prompt}`);
      if (noHistory) {
        const kvHit = await kvGetJson(env, `ai:cache:${cacheKey}`);
        if (kvHit?.reply) {
          return jsonResponse(request, { ok: true, model: kvHit.model, reply: kvHit.reply, cached: true, quota }, 200, requestId);
        }
        if (env.DB) {
          const hit = await env.DB.prepare("SELECT response_text, model FROM ai_response_cache WHERE cache_key = ?").bind(cacheKey).first();
          if (hit && hit.response_text) {
            return jsonResponse(request, { ok: true, model: hit.model, reply: hit.response_text, cached: true, quota }, 200, requestId);
          }
        }
      }

      const aiRes = await runCloudflareAiWithFallback(env, modelId, messages, 1200, intent);
      if (!aiRes.text) {
        return jsonResponse(request, { error: "AI_EXECUTION_FAILED", requestId }, 502, requestId);
      }
      const frenchReply = await enforceFrenchOutput(env, aiRes.text, intent);

      if (noHistory) {
        await kvPutJson(env, `ai:cache:${cacheKey}`, { model: aiRes.model, reply: frenchReply }, 3600);
        if (env.DB) {
          await env.DB.prepare(
            "INSERT OR REPLACE INTO ai_response_cache (cache_key, model, response_text, created_at_ms) VALUES (?, ?, ?, ?)"
          ).bind(cacheKey, aiRes.model, frenchReply, Date.now()).run();
        }
      }
      await enqueueTask(env, { type: "usage_flush", uid: user.uid, dayKey: quota.dayKey, model: aiRes.model });

      return jsonResponse(request, {
        ok: true,
        model: aiRes.model,
        reply: frenchReply,
        cached: false,
        gatewayUsed: aiRes.gatewayUsed,
        quota
      }, 200, requestId);
    }

    if (path === "/v1/ai/auto-reply" && request.method === "POST") {
      if (!env.AI) {
        return jsonResponse(request, { error: "AI_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const quota = await checkAndReserveAiQuota(env, user.uid);
      if (!quota.allowed) {
        return jsonResponse(request, { error: "QUOTA_EXCEEDED", quota, requestId }, 429, requestId);
      }

      let b = {};
      try { b = await request.json(); } catch {}
      const incomingMessage = String(b.incomingMessage || "").trim().slice(0, 2000);
      if (!incomingMessage) {
        return jsonResponse(request, { error: "INVALID_MESSAGE", requestId }, 400, requestId);
      }
      const channel = b.channel === "sms" ? "sms" : "brav_chat";
      const senderName = String(b.senderName || "Contact").trim().slice(0, 80);
      const tone = String(b.tone || "professionnel").trim().slice(0, 40);
      const instruction = String(b.instruction || "Réponds poliment, brièvement et utilement en mon nom.").trim().slice(0, 500);
      const recentReplies = Array.isArray(b.recentReplies) ? b.recentReplies.slice(-5).map((r) => String(r).slice(0, 200)) : [];
      const modelId = selectModelForIntent("auto_reply", incomingMessage, b.model);

      const maxCharsNote = channel === "sms"
        ? "Format SMS strict : 1 à 2 phrases courtes (maximum 260 caractères), sans Markdown ni préambule."
        : "Réponse naturelle et concise (maximum 400 caractères), sans préambule inutile.";

      const avoidRepeatNote = recentReplies.length > 0
        ? ` Évite de répéter ces formulations récentes : ${recentReplies.join(" | ")}.`
        : "";

      const messages = [
        {
          role: "system",
          content: `${STRICT_FRENCH_SYSTEM_DIRECTIVE} Tu rédiges une réponse automatique pour le compte de l'utilisateur sur ${channel === "sms" ? "SMS" : "BRAV Chat"}. Ton demandé : ${tone}. Consigne : ${instruction}. ${maxCharsNote}${avoidRepeatNote} Rédige exclusivement en français, même si le message reçu est dans une autre langue.`
        }
      ];
      if (Array.isArray(b.history)) {
        for (const h of b.history.slice(-4)) {
          const role = h?.role === "assistant" ? "assistant" : "user";
          const content = String(h?.content || "").trim().slice(0, 600);
          if (content) messages.push({ role, content });
        }
      }
      messages.push({
        role: "user",
        content: `Message reçu de ${senderName} : "${incomingMessage}"\nRédige uniquement en français le texte exact à envoyer.`
      });

      const aiRes = await runCloudflareAiWithFallback(env, modelId, messages, channel === "sms" ? 160 : 260, "auto_reply");
      if (!aiRes.text) {
        return jsonResponse(request, { error: "AI_AUTO_REPLY_FAILED", requestId }, 502, requestId);
      }
      const frenchAutoReply = await enforceFrenchOutput(env, aiRes.text, "auto_reply");
      return jsonResponse(request, {
        ok: true,
        model: aiRes.model,
        reply: frenchAutoReply,
        channel,
        tone,
        quota
      }, 200, requestId);
    }

    if (path === "/v1/ai/math" && request.method === "POST") {
      if (!env.AI) {
        return jsonResponse(request, { error: "AI_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const quota = await checkAndReserveAiQuota(env, user.uid);
      if (!quota.allowed) {
        return jsonResponse(request, { error: "QUOTA_EXCEEDED", quota, requestId }, 429, requestId);
      }

      let b = {};
      try { b = await request.json(); } catch {}
      const expression = String(b.expression || "").trim().slice(0, 3000);
      if (!expression) {
        return jsonResponse(request, { error: "INVALID_EXPRESSION", requestId }, 400, requestId);
      }
      const topic = String(b.topic || b.engine || "LaTeX Universel").trim().slice(0, 60);
      const localSummary = String(b.localSummary || "").trim().slice(0, 1500);
      const modelId = selectModelForIntent("latex_math", expression, b.model);

      const messages = [
        {
          role: "system",
          content:
            `${STRICT_FRENCH_SYSTEM_DIRECTIVE} Tu es le professeur de mathématiques universel de BRAV Chat. ${MATH_PEDAGOGY_DIRECTIVE} ` +
            "Utilise LaTeX valide uniquement à l'intérieur de $...$ ou $$...$$ pour le rendu visuel KaTeX (fractions superposées, racines, vecteurs, matrices, intégrales) et rédige toute l'explication en français simple, structuré et compréhensible : Énoncé, Données, Méthode, Résolution détaillée ligne par ligne, Résultat final et Interprétation."
        },
        {
          role: "user",
          content: `Domaine : ${topic}\nProblème ou expression : ${expression}${localSummary ? `\nCalcul numérique/symbolique vérifié localement : ${localSummary}` : ""}`
        }
      ];

      const aiRes = await runCloudflareAiWithFallback(env, modelId, messages, 1400, "latex_math");
      if (!aiRes.text) {
        return jsonResponse(request, { error: "AI_MATH_FAILED", requestId }, 502, requestId);
      }
      const frenchMath = await enforceFrenchOutput(env, aiRes.text, "latex_math");
      return jsonResponse(request, {
        ok: true,
        model: aiRes.model,
        reply: frenchMath,
        topic,
        sourceTag: "AI_ESTIMATED",
        quota
      }, 200, requestId);
    }

    if (path === "/v1/ai/document" && request.method === "POST") {
      if (!env.AI) {
        return jsonResponse(request, { error: "AI_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      const quota = await checkAndReserveAiQuota(env, user.uid);
      if (!quota.allowed) {
        return jsonResponse(request, { error: "QUOTA_EXCEEDED", quota, requestId }, 429, requestId);
      }

      let b = {};
      try { b = await request.json(); } catch {}
      const prompt = String(b.prompt || "").trim().slice(0, 3000);
      const templateType = String(b.templateType || "rapport").trim().slice(0, 40);
      if (!prompt) {
        return jsonResponse(request, { error: "INVALID_PROMPT", requestId }, 400, requestId);
      }
      const modelId = selectModelForIntent("document", prompt, b.model);
      const messages = [
        {
          role: "system",
          content: `${STRICT_FRENCH_SYSTEM_DIRECTIVE} Tu génères un document professionnel entièrement en français de type "${templateType}" en JSON valide UNIQUEMENT (sans bloc markdown). Structure exacte : {"title":"...","subtitle":"...","sections":[{"heading":"...","latex":"...","body":"...","tableRows":[["Col1","Col2"],["Val1","Val2"]]}]}. Rédige obligatoirement tous les textes (title, subtitle, heading, body, en-têtes de tableaux) en français. Utilise LaTeX standard dans "latex" quand il y a des formules.`
        },
        { role: "user", content: prompt }
      ];

      const aiRes = await runCloudflareAiWithFallback(env, modelId, messages, 1800, "document");
      if (!aiRes.text) {
        return jsonResponse(request, { error: "AI_DOC_FAILED", requestId }, 502, requestId);
      }
      let structured = null;
      try {
        const cleaned = aiRes.text.replace(/^```json\s*/i, "").replace(/```$/i, "").trim();
        structured = JSON.parse(cleaned);
      } catch {
        const frenchBody = await enforceFrenchOutput(env, aiRes.text, "document");
        structured = {
          title: prompt.slice(0, 80),
          subtitle: `Modèle : ${templateType}`,
          sections: [{ heading: "Contenu", latex: "", body: frenchBody, tableRows: [] }]
        };
      }
      return jsonResponse(request, { ok: true, model: aiRes.model, document: structured, quota }, 200, requestId);
    }

    if (path === "/v1/ai/translate" && request.method === "POST") {
      if (!env.AI) {
        return jsonResponse(request, { error: "AI_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      let b = {};
      try { b = await request.json(); } catch {}
      const text = String(b.text || "").trim();
      const targetLanguage = String(b.targetLanguage || "Français").trim().slice(0, 60);
      if (!text || text.length > 4000) {
        return jsonResponse(request, { error: "INVALID_TEXT", requestId }, 400, requestId);
      }
      const modelId = selectModelForIntent("translation", text, b.model);
      const cacheKey = await sha256Hex(`tr:${targetLanguage}:${text}`);
      const kvHit = await kvGetJson(env, `ai:tr:${cacheKey}`);
      if (kvHit?.text) {
        return jsonResponse(request, { ok: true, translatedText: kvHit.text, model: kvHit.model, cached: true }, 200, requestId);
      }
      if (env.DB) {
        const hit = await env.DB.prepare("SELECT response_text, model FROM ai_response_cache WHERE cache_key = ?").bind(cacheKey).first();
        if (hit && hit.response_text) {
          return jsonResponse(request, { ok: true, translatedText: hit.response_text, model: hit.model, cached: true }, 200, requestId);
        }
      }
      const aiRes = await runCloudflareAiWithFallback(
        env,
        modelId,
        [
          {
            role: "system",
            content: `You are a precise translator. Translate the user text into ${targetLanguage}. Output ONLY the exact translation without preamble, quotes, or explanation, preserving emojis and tone.`
          },
          { role: "user", content: text }
        ],
        512,
        "translation"
      );
      if (!aiRes.text) {
        return jsonResponse(request, { error: "AI_TRANSLATION_EMPTY", requestId }, 502, requestId);
      }
      await kvPutJson(env, `ai:tr:${cacheKey}`, { model: aiRes.model, text: aiRes.text }, 86400);
      if (env.DB) {
        await env.DB.prepare(
          "INSERT OR REPLACE INTO ai_response_cache (cache_key, model, response_text, created_at_ms) VALUES (?, ?, ?, ?)"
        ).bind(cacheKey, aiRes.model, aiRes.text, Date.now()).run();
      }
      return jsonResponse(request, { ok: true, translatedText: aiRes.text, model: aiRes.model, cached: false }, 200, requestId);
    }

    // =========================================================================
    // AUTO-REPLY PER-CONTACT RULES + DOCUMENTS META + LAUNCHER PROFILE
    // =========================================================================
    if (path === "/v1/auto-reply/rules" && request.method === "GET") {
      if (!env.DB) return jsonResponse(request, { rules: [] }, 200, requestId);
      const res = await env.DB.prepare(
        "SELECT contact_key AS contactKey, contact_label AS contactLabel, mode, tone, custom_instruction AS customInstruction, updated_at_ms AS updatedAtMs FROM auto_reply_rules WHERE user_id = ?"
      ).bind(user.uid).all();
      return jsonResponse(request, { rules: res.results || [] }, 200, requestId);
    }

    if (path === "/v1/auto-reply/rules" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const b = await request.json();
      const contactKey = String(b.contactKey || "").trim().slice(0, 128);
      if (!contactKey) return jsonResponse(request, { error: "MISSING_CONTACT_KEY", requestId }, 400, requestId);
      await env.DB.prepare(
        "INSERT OR REPLACE INTO auto_reply_rules (user_id, contact_key, contact_label, mode, tone, custom_instruction, updated_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?)"
      ).bind(
        user.uid,
        contactKey,
        String(b.contactLabel || "").slice(0, 120),
        String(b.mode || "auto").slice(0, 24),
        String(b.tone || "professionnel").slice(0, 32),
        String(b.customInstruction || "").slice(0, 500),
        Date.now()
      ).run();
      await kvPutJson(env, `rules:${user.uid}:${contactKey}`, b, 3600);
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/auto-reply/rules" && request.method === "DELETE") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const contactKey = (url.searchParams.get("contactKey") || "").trim();
      if (contactKey) {
        await env.DB.prepare("DELETE FROM auto_reply_rules WHERE user_id = ? AND contact_key = ?")
          .bind(user.uid, contactKey).run();
      }
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/documents" && request.method === "GET") {
      if (!env.DB) return jsonResponse(request, { documents: [] }, 200, requestId);
      const res = await env.DB.prepare(
        "SELECT id, title, doc_format AS docFormat, template_type AS templateType, object_key AS objectKey, size_bytes AS sizeBytes, created_at_ms AS createdAtMs FROM documents_meta WHERE user_id = ? ORDER BY created_at_ms DESC LIMIT 50"
      ).bind(user.uid).all();
      return jsonResponse(request, { documents: res.results || [] }, 200, requestId);
    }

    if (path === "/v1/documents" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const b = await request.json();
      const id = String(b.id || crypto.randomUUID());
      await env.DB.prepare(
        "INSERT OR REPLACE INTO documents_meta (id, user_id, title, doc_format, template_type, object_key, size_bytes, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      ).bind(
        id,
        user.uid,
        String(b.title || "Document BRAV").slice(0, 160),
        String(b.docFormat || "pdf").slice(0, 16),
        String(b.templateType || "rapport").slice(0, 40),
        String(b.objectKey || "").slice(0, 256),
        Number(b.sizeBytes || 0),
        Date.now()
      ).run();
      return jsonResponse(request, { ok: true, id }, 201, requestId);
    }

    if (path === "/v1/launcher/profile" && request.method === "GET") {
      const cached = await kvGetJson(env, `launcher:${user.uid}`);
      if (cached) return jsonResponse(request, { ok: true, profile: cached }, 200, requestId);
      if (!env.DB) return jsonResponse(request, { ok: true, profile: null }, 200, requestId);
      const row = await env.DB.prepare(
        "SELECT theme_id AS themeId, icon_shape AS iconShape, config_json AS configJson, updated_at_ms AS updatedAtMs FROM launcher_profiles WHERE user_id = ?"
      ).bind(user.uid).first();
      return jsonResponse(request, { ok: true, profile: row || null }, 200, requestId);
    }

    if (path === "/v1/launcher/profile" && request.method === "POST") {
      const b = await request.json();
      const payload = {
        themeId: String(b.themeId || "brav_signature").slice(0, 48),
        iconShape: String(b.iconShape || "squircle").slice(0, 32),
        configJson: String(b.configJson || "{}").slice(0, 16000),
        updatedAtMs: Date.now()
      };
      await kvPutJson(env, `launcher:${user.uid}`, payload, 3600);
      if (env.DB) {
        await env.DB.prepare(
          "INSERT OR REPLACE INTO launcher_profiles (user_id, theme_id, icon_shape, config_json, updated_at_ms) VALUES (?, ?, ?, ?, ?)"
        ).bind(user.uid, payload.themeId, payload.iconShape, payload.configJson, payload.updatedAtMs).run();
      }
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    // =========================================================================
    // SERVER-ENFORCED BLOCKS, DELETIONS, CONVERSATION STATES & LOGIN HISTORY
    // =========================================================================
    if (path === "/v1/blocks" && request.method === "GET") {
      if (!env.DB) return jsonResponse(request, { blocks: [] }, 200, requestId);
      const res = await env.DB.prepare(
        "SELECT blocked_uid AS blockedUid, reason, created_at_ms AS createdAtMs FROM blocked_users WHERE blocker_uid = ?"
      ).bind(user.uid).all();
      return jsonResponse(request, { blocks: res.results || [] }, 200, requestId);
    }

    if (path === "/v1/blocks" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const b = await request.json();
      const targetUid = String(b.targetUid || "").trim();
      if (!targetUid || targetUid === user.uid) {
        return jsonResponse(request, { error: "INVALID_TARGET", requestId }, 400, requestId);
      }
      await env.DB.prepare(
        "INSERT OR REPLACE INTO blocked_users (blocker_uid, blocked_uid, reason, created_at_ms) VALUES (?, ?, ?, ?)"
      ).bind(user.uid, targetUid, String(b.reason || "").slice(0, 300), Date.now()).run();
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/blocks" && request.method === "DELETE") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const targetUid = (url.searchParams.get("targetUid") || "").trim();
      if (targetUid) {
        await env.DB.prepare("DELETE FROM blocked_users WHERE blocker_uid = ? AND blocked_uid = ?")
          .bind(user.uid, targetUid).run();
      }
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/messages/delete" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const b = await request.json();
      const conversationId = String(b.conversationId || "").slice(0, 128);
      const messageId = String(b.messageId || "").slice(0, 128);
      const scope = b.scope === "all" ? "all" : "me";
      if (!conversationId || !messageId) {
        return jsonResponse(request, { error: "MISSING_IDS", requestId }, 400, requestId);
      }
      await env.DB.prepare(
        "INSERT OR REPLACE INTO deleted_messages (user_id, conversation_id, message_id, scope, deleted_at_ms) VALUES (?, ?, ?, ?, ?)"
      ).bind(user.uid, conversationId, messageId, scope, Date.now()).run();
      return jsonResponse(request, { ok: true, scope }, 200, requestId);
    }

    if (path === "/v1/conversations/state" && request.method === "GET") {
      if (!env.DB) return jsonResponse(request, { states: [] }, 200, requestId);
      const res = await env.DB.prepare(
        "SELECT conversation_id AS conversationId, archived, pinned, muted, locked, marked_unread AS markedUnread, hidden_locally AS hiddenLocally, last_read_at_ms AS lastReadAtMs, updated_at_ms AS updatedAtMs FROM conversation_user_states WHERE user_id = ?"
      ).bind(user.uid).all();
      return jsonResponse(request, { states: res.results || [] }, 200, requestId);
    }

    if (path === "/v1/conversations/state" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      const b = await request.json();
      const cid = String(b.conversationId || "").slice(0, 128);
      if (!cid) return jsonResponse(request, { error: "MISSING_CONVERSATION_ID", requestId }, 400, requestId);
      await env.DB.prepare(
        "INSERT OR REPLACE INTO conversation_user_states (user_id, conversation_id, archived, pinned, muted, locked, marked_unread, hidden_locally, last_read_at_ms, updated_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      ).bind(
        user.uid,
        cid,
        b.archived ? 1 : 0,
        b.pinned ? 1 : 0,
        b.muted ? 1 : 0,
        b.locked ? 1 : 0,
        b.markedUnread ? 1 : 0,
        b.hiddenLocally ? 1 : 0,
        Number(b.lastReadAtMs || 0),
        Date.now()
      ).run();
      return jsonResponse(request, { ok: true }, 200, requestId);
    }

    if (path === "/v1/sessions/history" && request.method === "GET") {
      if (!env.DB) return jsonResponse(request, { history: [] }, 200, requestId);
      const res = await env.DB.prepare(
        "SELECT id, device_id AS deviceId, device_name AS deviceName, country_code AS countryCode, city, created_at_ms AS createdAtMs FROM login_history WHERE user_id = ? ORDER BY created_at_ms DESC LIMIT 20"
      ).bind(user.uid).all();
      return jsonResponse(request, { history: res.results || [] }, 200, requestId);
    }

    if (path === "/v1/sessions/history" && request.method === "POST") {
      if (!env.DB) return jsonResponse(request, { ok: false }, 200, requestId);
      let b = {};
      try { b = await request.json(); } catch {}
      const cf = request.cf || {};
      const countryCode = String(b.countryCode || cf.country || "").slice(0, 10);
      const city = String(b.city || cf.city || "").slice(0, 80);
      const id = crypto.randomUUID();
      await env.DB.prepare(
        "INSERT INTO login_history (id, user_id, device_id, device_name, country_code, city, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?)"
      ).bind(
        id,
        user.uid,
        String(b.deviceId || "").slice(0, 120),
        String(b.deviceName || "Android").slice(0, 120),
        countryCode,
        city,
        Date.now()
      ).run();
      return jsonResponse(request, { ok: true, id, countryCode, city }, 201, requestId);
    }

    if (path === "/v1/lookup/phone" && request.method === "GET") {
      const raw = (url.searchParams.get("number") || "").trim();
      const cf = request.cf || {};
      return jsonResponse(request, {
        number: raw,
        suggestedCountry: cf.country || "",
        suggestedCity: cf.city || ""
      }, 200, requestId);
    }

    // =========================================================================
    // FOLLOW GRAPH & CERTIFICATION (5 000 FOLLOWERS, LIVE SELFIE, 3 600 F CFA / 5,99 USD)
    // =========================================================================
    if (path === "/v1/follows/toggle" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const targetUid = String(b.targetUid || "").trim().slice(0, 128);
      const follow = b.follow !== false;
      const isPrivate = b.isPrivate === true;
      if (!targetUid || targetUid === user.uid) {
        return jsonResponse(request, { error: "INVALID_FOLLOW_TARGET", requestId }, 400, requestId);
      }
      const now = Date.now();
      let followersCount = 0;
      let relationState = follow ? "following" : "not_following";

      if (follow && isPrivate) {
        const reqId = `${user.uid}_${targetUid}`;
        if (env.DB) {
          await env.DB.prepare(
            "INSERT OR REPLACE INTO follow_requests (id, from_uid, to_uid, status, created_at_ms, updated_at_ms) VALUES (?, ?, ?, 'pending', ?, ?)"
          ).bind(reqId, user.uid, targetUid, now, now).run();
        }
        followersCount = await getFollowerCount(env, targetUid);
        relationState = "requested";
        return jsonResponse(request, {
          ok: true,
          targetUid,
          following: false,
          requested: true,
          relationState,
          followersCount
        }, 200, requestId);
      }

      if (env.DB) {
        if (follow) {
          await env.DB.prepare(
            "INSERT OR IGNORE INTO user_follows (target_uid, follower_uid, created_at_ms) VALUES (?, ?, ?)"
          ).bind(targetUid, user.uid, now).run();
        } else {
          await env.DB.prepare(
            "DELETE FROM user_follows WHERE target_uid = ? AND follower_uid = ?"
          ).bind(targetUid, user.uid).run();
          await env.DB.prepare(
            "DELETE FROM follow_requests WHERE from_uid = ? AND to_uid = ? AND status = 'pending'"
          ).bind(user.uid, targetUid).run();
        }
        followersCount = await getFollowerCount(env, targetUid);
      } else {
        const setKey = `follows:list:${targetUid}`;
        const existingList = (await kvGetJson(env, setKey))?.followers || [];
        const nextSet = new Set(existingList);
        if (follow) nextSet.add(user.uid); else nextSet.delete(user.uid);
        const arr = [...nextSet];
        await kvPutJson(env, setKey, { followers: arr }, 86400 * 30);
        followersCount = arr.length;
      }
      await setKvFollowerCount(env, targetUid, followersCount);
      return jsonResponse(request, {
        ok: true,
        targetUid,
        following: follow,
        requested: false,
        relationState,
        followersCount
      }, 200, requestId);
    }

    if (path === "/v1/follows/request/respond" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const fromUid = String(b.fromUid || "").trim().slice(0, 128);
      const accept = b.accept === true;
      if (!fromUid || fromUid === user.uid) {
        return jsonResponse(request, { error: "INVALID_FROM_UID", requestId }, 400, requestId);
      }
      const now = Date.now();
      const reqId = `${fromUid}_${user.uid}`;
      if (env.DB) {
        await env.DB.prepare(
          "UPDATE follow_requests SET status = ?, updated_at_ms = ? WHERE id = ? AND to_uid = ?"
        ).bind(accept ? "accepted" : "declined", now, reqId, user.uid).run();
        if (accept) {
          await env.DB.prepare(
            "INSERT OR IGNORE INTO user_follows (target_uid, follower_uid, created_at_ms) VALUES (?, ?, ?)"
          ).bind(user.uid, fromUid, now).run();
        }
      }
      const followersCount = await getFollowerCount(env, user.uid);
      await setKvFollowerCount(env, user.uid, followersCount);
      return jsonResponse(request, { ok: true, accepted: accept, followersCount }, 200, requestId);
    }

    if (path === "/v1/invites/create" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const code = String(b.code || crypto.randomUUID().replace(/-/g, "").slice(0, 10)).replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 32);
      const displayName = String(b.inviterDisplayName || user.name || "Membre BRAV").slice(0, 120);
      const channel = String(b.channel || "share").slice(0, 40);
      const now = Date.now();
      if (env.DB) {
        await env.DB.prepare(
          "INSERT OR IGNORE INTO invite_links (code, inviter_uid, inviter_display_name, channel, clicks, signups, active, created_at_ms) VALUES (?, ?, ?, ?, 0, 0, 1, ?)"
        ).bind(code, user.uid, displayName, channel, now).run();
      }
      return jsonResponse(request, {
        ok: true,
        code,
        shareUrl: `https://bravchat.com/invite/${code}`
      }, 201, requestId);
    }

    if (path === "/v1/invites/track" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const code = String(b.code || "").replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 32);
      const event = b.event === "signup" ? "signup" : "click";
      if (!code) return jsonResponse(request, { error: "MISSING_INVITE_CODE", requestId }, 400, requestId);
      if (env.DB) {
        if (event === "signup") {
          await env.DB.prepare("UPDATE invite_links SET signups = signups + 1 WHERE code = ? AND active = 1").bind(code).run();
        } else {
          await env.DB.prepare("UPDATE invite_links SET clicks = clicks + 1 WHERE code = ? AND active = 1").bind(code).run();
        }
      }
      return jsonResponse(request, { ok: true, code, event }, 200, requestId);
    }

    if (path === "/v1/caller/lookup" && request.method === "GET") {
      const hash = (url.searchParams.get("hash") || "").trim().slice(0, 128);
      if (!hash) return jsonResponse(request, { found: false }, 200, requestId);
      if (env.DB) {
        const entry = await env.DB.prepare(
          "SELECT phone_lookup_hash AS phoneLookupHash, business_name AS businessName, category, city, country_iso AS countryIso, verified FROM caller_directory WHERE phone_lookup_hash = ?"
        ).bind(hash).first();
        if (entry) {
          return jsonResponse(request, { found: true, entry }, 200, requestId);
        }
      }
      return jsonResponse(request, { found: false }, 200, requestId);
    }

    const followCountMatch = path.match(/^\/v1\/follows\/count\/([a-zA-Z0-9_-]{1,128})$/);
    if (followCountMatch && request.method === "GET") {
      const targetUid = followCountMatch[1];
      const followersCount = await getFollowerCount(env, targetUid);
      return jsonResponse(request, { ok: true, targetUid, followersCount }, 200, requestId);
    }

    if (path === "/v1/verification/eligibility" && request.method === "GET") {
      const exempt = isDesignerExempt(user);
      const followersCount = await getFollowerCount(env, user.uid);
      const now = Date.now();
      let existingCase = null;
      if (env.DB) {
        existingCase = await env.DB.prepare(
          "SELECT * FROM certification_cases WHERE user_id = ?"
        ).bind(user.uid).first();
      } else {
        existingCase = await kvGetJson(env, `cert:case:${user.uid}`);
      }

      if (exempt) {
        const caseNumber = existingCase?.case_number || `BRV-CREATOR-${user.uid.slice(0, 6).toUpperCase()}`;
        const verifiedCase = {
          case_number: caseNumber,
          user_id: user.uid,
          category: "creator",
          followers_count: followersCount,
          status: "verified",
          selfie_key: "",
          selfie_score: 1.0,
          liveness_passed: 1,
          spoof_risk: "exempt_designer",
          terms_accepted: 1,
          selfie_consent: 1,
          payment_tx_ref: "EXEMPT_DESIGNER",
          payment_amount_xof: 0,
          payment_currency: "XOF",
          receipt_number: "",
          designer_exempt: 1,
          reviewer_note: "Compte concepteur officiel BRAV CHAT — certification automatique",
          created_at_ms: existingCase?.created_at_ms || now,
          updated_at_ms: now
        };
        if (env.DB) {
          await env.DB.prepare(
            "INSERT OR REPLACE INTO certification_cases (case_number, user_id, category, followers_count, status, selfie_key, selfie_score, liveness_passed, spoof_risk, terms_accepted, selfie_consent, payment_tx_ref, payment_amount_xof, payment_currency, receipt_number, designer_exempt, reviewer_note, created_at_ms, updated_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          ).bind(
            verifiedCase.case_number, verifiedCase.user_id, verifiedCase.category, verifiedCase.followers_count,
            verifiedCase.status, verifiedCase.selfie_key, verifiedCase.selfie_score, verifiedCase.liveness_passed,
            verifiedCase.spoof_risk, verifiedCase.terms_accepted, verifiedCase.selfie_consent,
            verifiedCase.payment_tx_ref, verifiedCase.payment_amount_xof, verifiedCase.payment_currency,
            verifiedCase.receipt_number, verifiedCase.designer_exempt, verifiedCase.reviewer_note,
            verifiedCase.created_at_ms, verifiedCase.updated_at_ms
          ).run();
        } else {
          await kvPutJson(env, `cert:case:${user.uid}`, verifiedCase, 86400 * 365);
        }
        return jsonResponse(request, {
          ok: true,
          exemptDesigner: true,
          eligible: true,
          status: "verified",
          caseNumber,
          followersCount,
          requiredFollowers: CERT_REQUIRED_FOLLOWERS,
          missingFollowers: 0,
          feeXof: 0,
          feeUsd: "0,00 USD",
          feeUsdCents: 0
        }, 200, requestId);
      }

      const eligible = followersCount >= CERT_REQUIRED_FOLLOWERS;
      const missingFollowers = Math.max(0, CERT_REQUIRED_FOLLOWERS - followersCount);
      const status = existingCase?.status || (eligible ? "eligible" : "not_eligible");

      return jsonResponse(request, {
        ok: true,
        exemptDesigner: false,
        eligible,
        status,
        caseNumber: existingCase?.case_number || "",
        followersCount,
        requiredFollowers: CERT_REQUIRED_FOLLOWERS,
        missingFollowers,
        feeXof: CERT_FEE_XOF,
        feeUsd: "5,99 USD",
        feeUsdCents: CERT_FEE_USD_CENTS,
        receiptNumber: existingCase?.receipt_number || "",
        paymentTxRef: existingCase?.payment_tx_ref || "",
        selfieScore: Number(existingCase?.selfie_score || 0)
      }, 200, requestId);
    }

    if (path === "/v1/verification/selfie" && request.method === "POST") {
      if (isDesignerExempt(user)) {
        return jsonResponse(request, { ok: true, exemptDesigner: true, status: "verified" }, 200, requestId);
      }
      let b = {};
      try { b = await request.json(); } catch {}
      const termsAccepted = b.termsAccepted === true;
      const selfieConsent = b.selfieConsent === true;
      const liveCameraOnly = b.liveCameraOnly === true;
      const galleryImportBlocked = b.galleryImportBlocked === true;
      if (!termsAccepted || !selfieConsent) {
        return jsonResponse(request, { error: "CONSENT_AND_TERMS_REQUIRED", requestId }, 400, requestId);
      }
      if (!liveCameraOnly || !galleryImportBlocked) {
        return jsonResponse(request, { error: "LIVE_CAMERA_ONLY_REQUIRED", requestId }, 403, requestId);
      }

      const followersCount = await getFollowerCount(env, user.uid);
      if (followersCount < CERT_REQUIRED_FOLLOWERS) {
        return jsonResponse(request, {
          error: "INSUFFICIENT_FOLLOWERS",
          followersCount,
          requiredFollowers: CERT_REQUIRED_FOLLOWERS,
          missingFollowers: CERT_REQUIRED_FOLLOWERS - followersCount,
          requestId
        }, 403, requestId);
      }

      const faceCount = Number(b.faceCount ?? 0);
      const livenessScore = Number(b.livenessScore ?? 0);
      const sharpnessScore = Number(b.sharpnessScore ?? 0);
      const luminance = Number(b.luminance ?? 0);
      const spoofRisk = String(b.spoofRisk || "low").slice(0, 32);
      const category = String(b.category || "creator").slice(0, 48);
      const imageBase64 = String(b.imageBase64 || "");

      if (faceCount !== 1 || livenessScore < 0.65 || sharpnessScore < 0.35 || luminance < 35 || luminance > 235 || spoofRisk === "high") {
        return jsonResponse(request, {
          ok: false,
          error: "SELFIE_VERIFICATION_FAILED",
          status: "retake_required",
          reason: faceCount !== 1
            ? "Un seul visage humain net et centré est requis."
            : "Éclairage ou netteté insuffisant(e) — reprenez un selfie en direct.",
          requestId
        }, 422, requestId);
      }

      if (!imageBase64 || imageBase64.length < 120) {
        return jsonResponse(request, { error: "EMPTY_SELFIE_CAPTURE", requestId }, 400, requestId);
      }

      const rawBin = atob(imageBase64);
      const rawBytes = new Uint8Array(rawBin.length);
      for (let i = 0; i < rawBin.length; i++) rawBytes[i] = rawBin.charCodeAt(i);
      const selfieKey = `private/verif/${user.uid}/${crypto.randomUUID()}.jpg`;
      if (env.R2) {
        await env.R2.put(selfieKey, rawBytes, {
          httpMetadata: {
            contentType: "image/jpeg",
            cacheControl: "no-store, private"
          },
          customMetadata: {
            ownerUid: user.uid,
            purpose: "brav_verified_live_selfie"
          }
        });
      }

      const now = Date.now();
      const caseNumber = `BRV-${new Date(now).getUTCFullYear()}-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;
      const record = {
        case_number: caseNumber,
        user_id: user.uid,
        category,
        followers_count: followersCount,
        status: "awaiting_payment",
        selfie_key: selfieKey,
        selfie_score: livenessScore,
        liveness_passed: 1,
        spoof_risk: spoofRisk,
        terms_accepted: 1,
        selfie_consent: 1,
        payment_tx_ref: "",
        payment_amount_xof: CERT_FEE_XOF,
        payment_currency: "XOF",
        receipt_number: "",
        designer_exempt: 0,
        reviewer_note: "",
        created_at_ms: now,
        updated_at_ms: now
      };
      if (env.DB) {
        await env.DB.prepare(
          "INSERT OR REPLACE INTO certification_cases (case_number, user_id, category, followers_count, status, selfie_key, selfie_score, liveness_passed, spoof_risk, terms_accepted, selfie_consent, payment_tx_ref, payment_amount_xof, payment_currency, receipt_number, designer_exempt, reviewer_note, created_at_ms, updated_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).bind(
          record.case_number, record.user_id, record.category, record.followers_count, record.status,
          record.selfie_key, record.selfie_score, record.liveness_passed, record.spoof_risk,
          record.terms_accepted, record.selfie_consent, record.payment_tx_ref, record.payment_amount_xof,
          record.payment_currency, record.receipt_number, record.designer_exempt, record.reviewer_note,
          record.created_at_ms, record.updated_at_ms
        ).run();
      } else {
        await kvPutJson(env, `cert:case:${user.uid}`, record, 86400 * 30);
      }

      return jsonResponse(request, {
        ok: true,
        caseNumber,
        status: "awaiting_payment",
        selfieKey,
        assessment: {
          faceCount,
          livenessScore,
          sharpnessScore,
          luminance,
          spoofRisk
        }
      }, 200, requestId);
    }

    if (path === "/v1/verification/payment/initiate" && request.method === "POST") {
      if (isDesignerExempt(user)) {
        return jsonResponse(request, { ok: true, exemptDesigner: true, status: "verified", amountXof: 0 }, 200, requestId);
      }
      let b = {};
      try { b = await request.json(); } catch {}
      const idempotencyKey = String(b.idempotencyKey || `${user.uid}_${Date.now()}`).slice(0, 128);
      const currency = b.currency === "USD" ? "USD" : "XOF";
      const provider = String(b.provider || "brav_mobile_money").slice(0, 64);

      const followersCount = await getFollowerCount(env, user.uid);
      if (followersCount < CERT_REQUIRED_FOLLOWERS) {
        return jsonResponse(request, {
          error: "INSUFFICIENT_FOLLOWERS",
          followersCount,
          requiredFollowers: CERT_REQUIRED_FOLLOWERS,
          requestId
        }, 403, requestId);
      }

      const existingCase = env.DB
        ? await env.DB.prepare("SELECT * FROM certification_cases WHERE user_id = ?").bind(user.uid).first()
        : await kvGetJson(env, `cert:case:${user.uid}`);

      if (!existingCase || existingCase.liveness_passed !== 1) {
        return jsonResponse(request, { error: "SELFIE_REQUIRED_BEFORE_PAYMENT", requestId }, 409, requestId);
      }

      if (env.DB) {
        const dup = await env.DB.prepare(
          "SELECT tx_ref, status, receipt_number FROM certification_payments WHERE idempotency_key = ? AND user_id = ?"
        ).bind(idempotencyKey, user.uid).first();
        if (dup) {
          return jsonResponse(request, {
            ok: true,
            txRef: dup.tx_ref,
            status: dup.status,
            receiptNumber: dup.receipt_number || "",
            amountXof: CERT_FEE_XOF,
            amountUsd: "5,99 USD",
            amountUsdCents: CERT_FEE_USD_CENTS,
            currency,
            idempotentReplay: true
          }, 200, requestId);
        }
      } else {
        const kvDup = await kvGetJson(env, `cert:pay:idem:${user.uid}:${idempotencyKey}`);
        if (kvDup) {
          return jsonResponse(request, { ...kvDup, ok: true, idempotentReplay: true }, 200, requestId);
        }
      }

      const now = Date.now();
      const txRef = `PAY-BRV-${crypto.randomUUID().slice(0, 10).toUpperCase()}`;
      const txPayload = {
        tx_ref: txRef,
        idempotency_key: idempotencyKey,
        user_id: user.uid,
        case_number: existingCase.case_number,
        amount_xof: CERT_FEE_XOF,
        amount_usd_cents: CERT_FEE_USD_CENTS,
        currency,
        provider,
        status: "pending",
        receipt_number: "",
        verified_at_ms: 0,
        created_at_ms: now
      };
      if (env.DB) {
        await env.DB.prepare(
          "INSERT INTO certification_payments (tx_ref, idempotency_key, user_id, case_number, amount_xof, amount_usd_cents, currency, provider, status, receipt_number, verified_at_ms, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).bind(
          txPayload.tx_ref, txPayload.idempotency_key, txPayload.user_id, txPayload.case_number,
          txPayload.amount_xof, txPayload.amount_usd_cents, txPayload.currency, txPayload.provider,
          txPayload.status, txPayload.receipt_number, txPayload.verified_at_ms, txPayload.created_at_ms
        ).run();
      } else {
        await kvPutJson(env, `cert:pay:${txRef}`, txPayload, 86400 * 30);
        await kvPutJson(env, `cert:pay:idem:${user.uid}:${idempotencyKey}`, {
          txRef,
          status: "pending",
          amountXof: CERT_FEE_XOF,
          amountUsd: "5,99 USD",
          amountUsdCents: CERT_FEE_USD_CENTS,
          currency
        }, 86400 * 30);
      }

      return jsonResponse(request, {
        ok: true,
        txRef,
        caseNumber: existingCase.case_number,
        amountXof: CERT_FEE_XOF,
        amountUsd: "5,99 USD",
        amountUsdCents: CERT_FEE_USD_CENTS,
        currency,
        status: "pending"
      }, 201, requestId);
    }

    if (path === "/v1/verification/payment/confirm" && request.method === "POST") {
      if (isDesignerExempt(user)) {
        return jsonResponse(request, { ok: true, exemptDesigner: true, status: "verified" }, 200, requestId);
      }
      let b = {};
      try { b = await request.json(); } catch {}
      const txRef = String(b.txRef || "").trim().slice(0, 64);
      const outcome = String(b.outcome || "confirmed").trim().toLowerCase();
      const paidAmountXof = Number(b.amountXof ?? CERT_FEE_XOF);
      const paidAmountUsdCents = Number(b.amountUsdCents ?? CERT_FEE_USD_CENTS);
      const currency = b.currency === "USD" ? "USD" : "XOF";

      if (!txRef) {
        return jsonResponse(request, { error: "MISSING_TX_REF", requestId }, 400, requestId);
      }

      const tx = env.DB
        ? await env.DB.prepare("SELECT * FROM certification_payments WHERE tx_ref = ? AND user_id = ?").bind(txRef, user.uid).first()
        : await kvGetJson(env, `cert:pay:${txRef}`);

      if (!tx || tx.user_id !== user.uid) {
        return jsonResponse(request, { error: "PAYMENT_NOT_FOUND", requestId }, 404, requestId);
      }

      if (tx.status === "confirmed" && tx.receipt_number) {
        return jsonResponse(request, {
          ok: true,
          status: "verified",
          idempotentReplay: true,
          receipt: {
            receiptNumber: tx.receipt_number,
            txRef: tx.tx_ref,
            amountXof: tx.amount_xof,
            amountUsd: "5,99 USD",
            currency: tx.currency,
            verifiedAtMs: tx.verified_at_ms
          }
        }, 200, requestId);
      }

      if (outcome === "failed" || outcome === "cancelled") {
        if (env.DB) {
          await env.DB.prepare("UPDATE certification_payments SET status = ? WHERE tx_ref = ?").bind(outcome, txRef).run();
        } else {
          tx.status = outcome;
          await kvPutJson(env, `cert:pay:${txRef}`, tx, 86400 * 30);
        }
        return jsonResponse(request, {
          ok: false,
          error: outcome === "cancelled" ? "PAYMENT_CANCELLED" : "PAYMENT_FAILED",
          status: "awaiting_payment",
          badgeGranted: false,
          requestId
        }, 402, requestId);
      }

      const validAmount = currency === "USD"
        ? paidAmountUsdCents === CERT_FEE_USD_CENTS && tx.amount_usd_cents === CERT_FEE_USD_CENTS
        : paidAmountXof === CERT_FEE_XOF && tx.amount_xof === CERT_FEE_XOF;
      if (!validAmount) {
        return jsonResponse(request, { error: "PAYMENT_AMOUNT_MISMATCH", requestId }, 400, requestId);
      }

      const followersCount = await getFollowerCount(env, user.uid);
      if (followersCount < CERT_REQUIRED_FOLLOWERS) {
        return jsonResponse(request, {
          error: "FINAL_ELIGIBILITY_FAILED_FOLLOWERS",
          followersCount,
          requiredFollowers: CERT_REQUIRED_FOLLOWERS,
          badgeGranted: false,
          requestId
        }, 403, requestId);
      }

      const existingCase = env.DB
        ? await env.DB.prepare("SELECT * FROM certification_cases WHERE user_id = ?").bind(user.uid).first()
        : await kvGetJson(env, `cert:case:${user.uid}`);

      if (!existingCase || existingCase.liveness_passed !== 1) {
        return jsonResponse(request, { error: "FINAL_ELIGIBILITY_FAILED_SELFIE", badgeGranted: false, requestId }, 403, requestId);
      }

      const now = Date.now();
      const receiptNumber = `REC-BRV-${now.toString().slice(-6)}-${txRef.slice(-4)}`;
      if (env.DB) {
        await env.DB.prepare(
          "UPDATE certification_payments SET status = 'confirmed', receipt_number = ?, verified_at_ms = ? WHERE tx_ref = ?"
        ).bind(receiptNumber, now, txRef).run();
        await env.DB.prepare(
          "UPDATE certification_cases SET status = 'verified', payment_tx_ref = ?, payment_amount_xof = ?, payment_currency = ?, receipt_number = ?, followers_count = ?, updated_at_ms = ? WHERE user_id = ?"
        ).bind(txRef, CERT_FEE_XOF, currency, receiptNumber, followersCount, now, user.uid).run();
      } else {
        tx.status = "confirmed";
        tx.receipt_number = receiptNumber;
        tx.verified_at_ms = now;
        await kvPutJson(env, `cert:pay:${txRef}`, tx, 86400 * 30);
        existingCase.status = "verified";
        existingCase.payment_tx_ref = txRef;
        existingCase.receipt_number = receiptNumber;
        existingCase.followers_count = followersCount;
        existingCase.updated_at_ms = now;
        await kvPutJson(env, `cert:case:${user.uid}`, existingCase, 86400 * 365);
      }

      await emitWorkerNotification(env, {
        targetUid: user.uid,
        senderUid: "system",
        senderName: "BRAV CHAT",
        category: "account_security",
        type: "certification_verified",
        title: "CERTIFICATION confirmée",
        body: `Paiement confirmé (${receiptNumber}). Votre badge bleu officiel est maintenant actif.`,
        refId: existingCase.case_number,
        refType: "certification",
        dedupKey: `cert_verified:${txRef}`,
        priority: "critical"
      });

      return jsonResponse(request, {
        ok: true,
        status: "verified",
        badgeGranted: true,
        followersCount,
        verifiedAtMs: now,
        receipt: {
          receiptNumber,
          txRef,
          caseNumber: existingCase.case_number,
          amountXof: CERT_FEE_XOF,
          amountUsd: "5,99 USD",
          currency,
          verifiedAtMs: now
        }
      }, 200, requestId);
    }

    if (path === "/v1/verification/admin-decision" && request.method === "POST") {
      if (!isAdminUser(user, env)) {
        return jsonResponse(request, { error: "ADMIN_REQUIRED", requestId }, 403, requestId);
      }
      let b = {};
      try { b = await request.json(); } catch {}
      const targetUid = String(b.targetUid || "").trim().slice(0, 128);
      const status = String(b.status || "").trim();
      const reviewerNote = String(b.reviewerNote || "").slice(0, 600);
      const allowedStatuses = new Set(["verified", "retake_required", "rejected", "suspended", "revoked", "in_review", "expired"]);
      if (!targetUid || !allowedStatuses.has(status)) {
        return jsonResponse(request, { error: "INVALID_ADMIN_DECISION", requestId }, 400, requestId);
      }
      const now = Date.now();
      if (env.DB) {
        await env.DB.prepare(
          "UPDATE certification_cases SET status = ?, reviewer_note = ?, updated_at_ms = ? WHERE user_id = ?"
        ).bind(status, reviewerNote, now, targetUid).run();
      } else {
        const c = await kvGetJson(env, `cert:case:${targetUid}`);
        if (c) {
          c.status = status;
          c.reviewer_note = reviewerNote;
          c.updated_at_ms = now;
          await kvPutJson(env, `cert:case:${targetUid}`, c, 86400 * 365);
        }
      }
      await emitWorkerNotification(env, {
        targetUid,
        senderUid: user.uid,
        senderName: "BRAV CHAT",
        category: "account_security",
        type: "certification_decision",
        title: status === "verified" ? "CERTIFICATION validée" : "CERTIFICATION — Mise à jour",
        body: reviewerNote || (status === "verified"
          ? "Votre demande de certification a été validée."
          : `Statut de votre certification : ${status}`),
        refId: targetUid,
        refType: "certification",
        dedupKey: `cert_decision:${targetUid}:${status}:${now}`,
        priority: "critical"
      });
      return jsonResponse(request, { ok: true, targetUid, status, reviewerNote, updatedAtMs: now }, 200, requestId);
    }

    // =========================================================================
    // CENTRALIZED NOTIFICATIONS ENGINE (REALTIME + D1 LEDGER + DEDUP + SYNC)
    // =========================================================================
    if (path === "/v1/notifications" && request.method === "GET") {
      const categoryFilter = String(url.searchParams.get("category") || "").trim();
      const limit = Math.min(100, Math.max(1, Number(url.searchParams.get("limit") || "50")));
      let items = [];
      if (env.DB) {
        const res = categoryFilter && VALID_NOTIFICATION_CATEGORIES.has(categoryFilter)
          ? await env.DB.prepare(
              "SELECT id, user_id AS userId, sender_uid AS senderUid, sender_name AS senderName, sender_avatar AS senderAvatar, category, type, title, body, ref_id AS refId, ref_type AS refType, sub_ref_id AS subRefId, dedup_key AS dedupKey, priority, is_read AS isRead, read_at_ms AS readAtMs, created_at_ms AS createdAtMs FROM notifications WHERE user_id = ? AND category = ? ORDER BY created_at_ms DESC LIMIT ?"
            ).bind(user.uid, categoryFilter, limit).all()
          : await env.DB.prepare(
              "SELECT id, user_id AS userId, sender_uid AS senderUid, sender_name AS senderName, sender_avatar AS senderAvatar, category, type, title, body, ref_id AS refId, ref_type AS refType, sub_ref_id AS subRefId, dedup_key AS dedupKey, priority, is_read AS isRead, read_at_ms AS readAtMs, created_at_ms AS createdAtMs FROM notifications WHERE user_id = ? ORDER BY created_at_ms DESC LIMIT ?"
            ).bind(user.uid, limit).all();
        items = (res.results || []).map((r) => ({ ...r, isRead: Boolean(r.isRead) }));
      } else {
        const all = (await kvGetJson(env, `notifs:${user.uid}`))?.items || [];
        items = categoryFilter ? all.filter((n) => n.category === categoryFilter).slice(0, limit) : all.slice(0, limit);
      }
      const unreadCount = items.filter((i) => !i.isRead).length;
      return jsonResponse(request, { ok: true, notifications: items, unreadCount }, 200, requestId);
    }

    if (path === "/v1/notifications/emit" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const targetUid = String(b.targetUid || user.uid).trim().slice(0, 128);
      const category = String(b.category || "messages").trim();
      if (category === "account_security" && targetUid !== user.uid && !isAdminUser(user, env)) {
        return jsonResponse(request, { error: "SECURITY_ALERT_FORBIDDEN", requestId }, 403, requestId);
      }
      const title = String(b.title || "").trim();
      const body = String(b.body || "").trim();
      if (!targetUid || !title || !body) {
        return jsonResponse(request, { error: "INVALID_NOTIFICATION_PAYLOAD", requestId }, 400, requestId);
      }
      const result = await emitWorkerNotification(env, {
        targetUid,
        senderUid: targetUid === user.uid ? String(b.senderUid || user.uid) : user.uid,
        senderName: String(b.senderName || ""),
        senderAvatar: String(b.senderAvatar || ""),
        category,
        type: String(b.type || "event"),
        title,
        body,
        refId: String(b.refId || ""),
        refType: String(b.refType || ""),
        subRefId: String(b.subRefId || ""),
        dedupKey: String(b.dedupKey || ""),
        priority: String(b.priority || "normal")
      });
      return jsonResponse(request, { ok: true, ...result }, 201, requestId);
    }

    if (path === "/v1/notifications/read" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const ids = Array.isArray(b.ids) ? b.ids.map((id) => String(id)).filter(Boolean).slice(0, 100) : [];
      const refId = String(b.refId || "").trim().slice(0, 160);
      const now = Date.now();
      if (env.DB) {
        if (ids.length > 0) {
          const ph = ids.map(() => "?").join(",");
          await env.DB.prepare(
            `UPDATE notifications SET is_read = 1, read_at_ms = ? WHERE user_id = ? AND id IN (${ph})`
          ).bind(now, user.uid, ...ids).run();
        } else if (refId) {
          await env.DB.prepare(
            "UPDATE notifications SET is_read = 1, read_at_ms = ? WHERE user_id = ? AND ref_id = ?"
          ).bind(now, user.uid, refId).run();
        }
      } else {
        const kvKey = `notifs:${user.uid}`;
        const existing = (await kvGetJson(env, kvKey))?.items || [];
        const idSet = new Set(ids);
        const updated = existing.map((n) => {
          if ((ids.length > 0 && idSet.has(n.id)) || (refId && n.refId === refId)) {
            return { ...n, isRead: true, readAtMs: now };
          }
          return n;
        });
        await kvPutJson(env, kvKey, { items: updated }, 86400 * 30);
      }
      await pushToUserHub(env, user.uid, { type: "notifications_read", ids, refId, readAtMs: now });
      return jsonResponse(request, { ok: true, readAtMs: now }, 200, requestId);
    }

    if (path === "/v1/notifications/read-all" && request.method === "POST") {
      let b = {};
      try { b = await request.json(); } catch {}
      const category = String(b.category || "").trim();
      const now = Date.now();
      if (env.DB) {
        if (category && VALID_NOTIFICATION_CATEGORIES.has(category)) {
          await env.DB.prepare(
            "UPDATE notifications SET is_read = 1, read_at_ms = ? WHERE user_id = ? AND category = ? AND is_read = 0"
          ).bind(now, user.uid, category).run();
        } else {
          await env.DB.prepare(
            "UPDATE notifications SET is_read = 1, read_at_ms = ? WHERE user_id = ? AND is_read = 0"
          ).bind(now, user.uid).run();
        }
      } else {
        const kvKey = `notifs:${user.uid}`;
        const existing = (await kvGetJson(env, kvKey))?.items || [];
        const updated = existing.map((n) => (!category || n.category === category ? { ...n, isRead: true, readAtMs: now } : n));
        await kvPutJson(env, kvKey, { items: updated }, 86400 * 30);
      }
      await pushToUserHub(env, user.uid, { type: "notifications_read_all", category, readAtMs: now });
      return jsonResponse(request, { ok: true, readAtMs: now }, 200, requestId);
    }

    const notifDeleteMatch = path.match(/^\/v1\/notifications\/([a-zA-Z0-9_-]{1,128})$/);
    if (notifDeleteMatch && request.method === "DELETE") {
      const notifId = notifDeleteMatch[1];
      if (env.DB) {
        await env.DB.prepare("DELETE FROM notifications WHERE id = ? AND user_id = ?").bind(notifId, user.uid).run();
      } else {
        const kvKey = `notifs:${user.uid}`;
        const existing = (await kvGetJson(env, kvKey))?.items || [];
        await kvPutJson(env, kvKey, { items: existing.filter((n) => n.id !== notifId) }, 86400 * 30);
      }
      return jsonResponse(request, { ok: true, id: notifId }, 200, requestId);
    }

    return jsonResponse(request, { error: "NOT_FOUND", requestId }, 404, requestId);
  },

  async queue(batch, env) {
    for (const message of batch.messages) {
      const payload = message.body || {};
      try {
        if (payload.type === "usage_flush" && env.DB && payload.uid && payload.dayKey) {
          await env.DB.prepare(
            "INSERT INTO ai_usage_metrics (user_id, day_key, request_count, last_model, updated_at_ms) VALUES (?, ?, 1, ?, ?) ON CONFLICT(user_id, day_key) DO UPDATE SET request_count = request_count + 1, last_model = excluded.last_model, updated_at_ms = excluded.updated_at_ms"
          ).bind(String(payload.uid), String(payload.dayKey), String(payload.model || ""), Date.now()).run();
        } else if (payload.type === "chat_notify_fanout" && Array.isArray(payload.recipients) && payload.event) {
          await Promise.all(payload.recipients.map((uid) => pushToUserHub(env, uid, payload.event)));
        } else if (payload.type === "push_missed_fallback" && payload.targetUid && payload.event) {
          await pushToUserHub(env, payload.targetUid, payload.event);
        }
        message.ack();
      } catch {
        message.retry();
      }
    }
  }
};

// =========================================================================
// DURABLE OBJECT: UserHub (Instant typing, receipts, presence, notifications)
// =========================================================================
export class UserHub {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sockets = new Set();
    this.uid = null;
    this.presence = { online: false, lastSeen: 0 };
  }

  async ensureUid(fallbackUid = "") {
    if (!this.uid) {
      this.uid = (await this.state.storage.get("uid")) || null;
    }
    if (fallbackUid && this.uid !== fallbackUid) {
      this.uid = fallbackUid;
      await this.state.storage.put("uid", fallbackUid);
    }
    const storedPresence = await this.state.storage.get("presence");
    if (storedPresence) this.presence = storedPresence;
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/internal/push" && request.method === "POST") {
      const payloadText = await request.text();
      let delivered = 0;
      for (const sock of this.sockets) {
        try {
          sock.send(payloadText);
          delivered++;
        } catch {
          this.sockets.delete(sock);
        }
      }
      return new Response(JSON.stringify({ ok: true, delivered }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    }

    if (url.pathname === "/internal/watch" && request.method === "POST") {
      await this.ensureUid();
      const body = await request.json().catch(() => ({}));
      const watcher = String(body.watcher || "").trim();
      if (watcher && watcher !== this.uid) {
        const watchers = (await this.state.storage.get("watchers")) || [];
        if (!watchers.includes(watcher)) {
          watchers.push(watcher);
          while (watchers.length > 200) watchers.shift();
          await this.state.storage.put("watchers", watchers);
        }
      }
      const isOnlineNow = this.sockets.size > 0 || Boolean(this.presence.online);
      return new Response(JSON.stringify({
        uid: this.uid || "",
        online: isOnlineNow,
        lastSeen: this.presence.lastSeen || 0
      }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    }

    if ((request.headers.get("Upgrade") || "").toLowerCase() !== "websocket") {
      return new Response("Upgrade required", { status: 426 });
    }

    const uid = request.headers.get("X-Brav-Uid") || "";
    const deviceId = request.headers.get("X-Brav-Device") || "android";
    await this.ensureUid(uid);

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();
    this.sockets.add(server);

    await this.updatePresence(true);

    server.send(JSON.stringify({
      type: "ready",
      uid,
      deviceId,
      connections: this.sockets.size
    }));

    server.addEventListener("message", async (ev) => {
      if (ev.data === "ping") {
        server.send("pong");
        return;
      }
      let msg;
      try {
        msg = JSON.parse(typeof ev.data === "string" ? ev.data : "");
      } catch {
        return;
      }
      if (!msg || typeof msg.type !== "string") return;

      if (msg.type === "ping") {
        server.send(JSON.stringify({ type: "pong", timestamp: Date.now() }));
        return;
      }

      if (msg.type === "typing" && msg.to && msg.conversationId) {
        await pushToUserHub(this.env, String(msg.to), {
          type: "typing",
          conversationId: String(msg.conversationId),
          from: uid,
          isTyping: msg.isTyping !== false,
          sentAt: Date.now()
        });
        return;
      }

      if (msg.type === "receipt" && msg.to && msg.conversationId && msg.messageId) {
        await pushToUserHub(this.env, String(msg.to), {
          type: "receipt",
          conversationId: String(msg.conversationId),
          messageId: String(msg.messageId),
          status: String(msg.status || "read"),
          from: uid,
          sentAt: Date.now()
        });
        return;
      }

      if (msg.type === "watch" && Array.isArray(msg.uids)) {
        const targets = [...new Set(msg.uids.map((u) => String(u || "").trim()).filter((u) => u && u !== uid))].slice(0, 100);
        const statuses = await Promise.all(targets.map(async (targetUid) => {
          try {
            if (this.env.USER_HUBS) {
              const hub = this.env.USER_HUBS.get(this.env.USER_HUBS.idFromName(targetUid));
              const res = await hub.fetch(new Request("https://user-hub.internal/internal/watch", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ watcher: uid })
              }));
              if (res.ok) {
                const d = await res.json();
                return {
                  uid: targetUid,
                  online: Boolean(d.online),
                  lastSeen: Number(d.lastSeen || 0)
                };
              }
            }
          } catch {}
          return { uid: targetUid, online: false, lastSeen: 0 };
        }));

        if (this.env.DB && targets.length > 0) {
          try {
            const ph = targets.map(() => "?").join(",");
            const rows = await this.env.DB.prepare(
              `SELECT user_id AS uid, online, last_seen_ms AS lastSeen FROM users_index WHERE user_id IN (${ph})`
            ).bind(...targets).all();
            const d1Map = new Map((rows.results || []).map((r) => [r.uid, r]));
            for (const s of statuses) {
              const row = d1Map.get(s.uid);
              if (row && !s.online && s.lastSeen === 0) {
                s.lastSeen = Number(row.lastSeen || 0);
              }
            }
          } catch {}
        }

        server.send(JSON.stringify({ type: "presence_snapshot", users: statuses }));
      }
    });

    const handleDisconnect = async () => {
      this.sockets.delete(server);
      if (this.sockets.size === 0) {
        await this.updatePresence(false);
      }
    };
    server.addEventListener("close", handleDisconnect);
    server.addEventListener("error", handleDisconnect);

    return new Response(null, { status: 101, webSocket: client, headers: { "Sec-WebSocket-Protocol": WS_SUBPROTOCOL } });
  }

  async updatePresence(online) {
    const now = Date.now();
    this.presence = { online: Boolean(online), lastSeen: now };
    await this.state.storage.put("presence", this.presence);

    if (this.env?.DB && this.uid) {
      try {
        await this.env.DB.prepare(
          "UPDATE users_index SET online = ?, last_seen_ms = ? WHERE user_id = ?"
        ).bind(online ? 1 : 0, now, this.uid).run();
      } catch {}
    }

    const watchers = (await this.state.storage.get("watchers")) || [];
    if (watchers.length > 0 && this.uid) {
      const event = { type: "presence", uid: this.uid, online: Boolean(online), lastSeen: now };
      await Promise.all(watchers.map((w) => pushToUserHub(this.env, w, event)));
    }
  }
}

// =========================================================================
// DURABLE OBJECT: CallRoom (WebRTC low-latency signaling + offline buffering)
// =========================================================================
export class CallRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.peers = new Map();
    this.earlySignals = [];
  }

  async fetch(request) {
    if ((request.headers.get("Upgrade") || "").toLowerCase() !== "websocket") {
      return new Response("Upgrade required", { status: 426 });
    }
    const uid = request.headers.get("X-Brav-Uid") || crypto.randomUUID();
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();

    for (const [existingSock, existingUid] of this.peers.entries()) {
      if (existingUid === uid) {
        this.peers.delete(existingSock);
        try { existingSock.close(4000, "replaced"); } catch {}
      }
    }

    this.peers.set(server, uid);

    server.send(JSON.stringify({ type: "room-ready", peers: this.peers.size }));
    for (const [peerSock] of this.peers.entries()) {
      if (peerSock !== server) {
        try { peerSock.send(JSON.stringify({ type: "peer-joined", uid })); } catch {}
      }
    }

    const forThisPeer = this.earlySignals.filter((item) => item.fromUid !== uid);
    if (forThisPeer.length > 0) {
      this.earlySignals = this.earlySignals.filter((item) => item.fromUid === uid);
      for (const item of forThisPeer) {
        try { server.send(item.raw); } catch {}
      }
    }

    server.addEventListener("message", (ev) => {
      if (ev.data === "ping") {
        server.send("pong");
        return;
      }
      let sentToOther = false;
      for (const [peerSock, peerUid] of this.peers.entries()) {
        if (peerSock !== server && peerUid !== uid) {
          try {
            peerSock.send(ev.data);
            sentToOther = true;
          } catch {}
        }
      }
      if (!sentToOther && typeof ev.data === "string") {
        if (this.earlySignals.length < 40) {
          this.earlySignals.push({ fromUid: uid, raw: ev.data });
        }
      }
    });

    const cleanup = () => {
      this.peers.delete(server);
      for (const [peerSock] of this.peers.entries()) {
        try { peerSock.send(JSON.stringify({ type: "peer-left", uid })); } catch {}
      }
    };
    server.addEventListener("close", cleanup);
    server.addEventListener("error", cleanup);

    return new Response(null, { status: 101, webSocket: client, headers: { "Sec-WebSocket-Protocol": WS_SUBPROTOCOL } });
  }
}
