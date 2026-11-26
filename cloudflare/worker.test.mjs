import test from "node:test";
import assert from "node:assert/strict";
import worker, {
  FREE_AI_MODELS,
  selectModelForIntent,
  runCloudflareAiWithFallback,
  stripAiReasoningTags,
  looksPredominantlyEnglish
} from "./worker.js";

function createMockKv() {
  const store = new Map();
  return {
    async get(key) {
      return store.get(key) ?? null;
    },
    async put(key, val) {
      store.set(key, val);
    },
    _store: store
  };
}

function createMockDb() {
  const tables = {
    ai_response_cache: new Map(),
    auto_reply_rules: [],
    documents_meta: [],
    launcher_profiles: new Map(),
    user_follows: new Map(), // key `${targetUid}:${followerUid}`
    certification_cases: new Map(), // key userId
    certification_payments: new Map(), // key txRef
    notifications: []
  };
  return {
    _tables: tables,
    prepare(sql) {
      return {
        _args: [],
        bind(...args) {
          this._args = args;
          return this;
        },
        async first() {
          if (sql.includes("FROM ai_response_cache")) {
            return tables.ai_response_cache.get(this._args[0]) ?? null;
          }
          if (sql.includes("FROM launcher_profiles")) {
            return tables.launcher_profiles.get(this._args[0]) ?? null;
          }
          if (sql.includes("COUNT(*) AS cnt FROM user_follows")) {
            const target = this._args[0];
            let cnt = 0;
            for (const row of tables.user_follows.values()) {
              if (row.target_uid === target) cnt++;
            }
            return { cnt };
          }
          if (sql.includes("FROM certification_cases WHERE user_id")) {
            return tables.certification_cases.get(this._args[0]) ?? null;
          }
          if (sql.includes("FROM certification_payments WHERE idempotency_key")) {
            const [idemKey, uid] = this._args;
            for (const p of tables.certification_payments.values()) {
              if (p.idempotency_key === idemKey && p.user_id === uid) return p;
            }
            return null;
          }
          if (sql.includes("FROM certification_payments WHERE tx_ref")) {
            const [txRef, uid] = this._args;
            const found = tables.certification_payments.get(txRef);
            return found && found.user_id === uid ? found : null;
          }
          if (sql.includes("SELECT id FROM notifications WHERE user_id = ? AND dedup_key = ?")) {
            const [uid, dedupKey] = this._args;
            return tables.notifications.find((n) => n.userId === uid && n.dedupKey === dedupKey) ?? null;
          }
          return null;
        },
        async all() {
          if (sql.includes("FROM auto_reply_rules")) {
            return { results: tables.auto_reply_rules.filter((r) => r.userId === this._args[0]) };
          }
          if (sql.includes("FROM documents_meta")) {
            return { results: tables.documents_meta.filter((d) => d.userId === this._args[0]) };
          }
          if (sql.includes("FROM notifications WHERE user_id = ? AND category = ?")) {
            const [uid, cat, limit] = this._args;
            return { results: tables.notifications.filter((n) => n.userId === uid && n.category === cat).slice(0, limit) };
          }
          if (sql.includes("FROM notifications WHERE user_id = ?")) {
            const [uid, limit] = this._args;
            return { results: tables.notifications.filter((n) => n.userId === uid).slice(0, limit) };
          }
          return { results: [] };
        },
        async run() {
          if (sql.includes("INSERT OR REPLACE INTO ai_response_cache")) {
            tables.ai_response_cache.set(this._args[0], {
              model: this._args[1],
              response_text: this._args[2]
            });
          } else if (sql.includes("INSERT OR REPLACE INTO auto_reply_rules")) {
            tables.auto_reply_rules.push({
              userId: this._args[0],
              contactKey: this._args[1],
              contactLabel: this._args[2],
              mode: this._args[3],
              tone: this._args[4],
              customInstruction: this._args[5]
            });
          } else if (sql.includes("INSERT OR REPLACE INTO documents_meta")) {
            tables.documents_meta.push({
              id: this._args[0],
              userId: this._args[1],
              title: this._args[2],
              docFormat: this._args[3],
              templateType: this._args[4]
            });
          } else if (sql.includes("INSERT OR REPLACE INTO launcher_profiles")) {
            tables.launcher_profiles.set(this._args[0], {
              themeId: this._args[1],
              iconShape: this._args[2],
              configJson: this._args[3]
            });
          } else if (sql.includes("INSERT OR IGNORE INTO user_follows")) {
            const [targetUid, followerUid, createdAt] = this._args;
            tables.user_follows.set(`${targetUid}:${followerUid}`, {
              target_uid: targetUid,
              follower_uid: followerUid,
              created_at_ms: createdAt
            });
          } else if (sql.includes("DELETE FROM user_follows")) {
            const [targetUid, followerUid] = this._args;
            tables.user_follows.delete(`${targetUid}:${followerUid}`);
          } else if (sql.includes("INSERT OR REPLACE INTO certification_cases")) {
            const a = this._args;
            tables.certification_cases.set(a[1], {
              case_number: a[0],
              user_id: a[1],
              category: a[2],
              followers_count: a[3],
              status: a[4],
              selfie_key: a[5],
              selfie_score: a[6],
              liveness_passed: a[7],
              spoof_risk: a[8],
              terms_accepted: a[9],
              selfie_consent: a[10],
              payment_tx_ref: a[11],
              payment_amount_xof: a[12],
              payment_currency: a[13],
              receipt_number: a[14],
              designer_exempt: a[15],
              reviewer_note: a[16],
              created_at_ms: a[17],
              updated_at_ms: a[18]
            });
          } else if (sql.includes("UPDATE certification_cases SET status = 'verified'")) {
            const [txRef, receiptNumber, followersCount, updatedAt, uid] = this._args;
            const existing = tables.certification_cases.get(uid);
            if (existing) {
              existing.status = "verified";
              existing.payment_tx_ref = txRef;
              existing.receipt_number = receiptNumber;
              existing.followers_count = followersCount;
              existing.updated_at_ms = updatedAt;
            }
          } else if (sql.includes("INSERT INTO certification_payments")) {
            const a = this._args;
            tables.certification_payments.set(a[0], {
              tx_ref: a[0],
              idempotency_key: a[1],
              user_id: a[2],
              case_number: a[3],
              amount_xof: a[4],
              amount_usd_cents: a[5],
              currency: a[6],
              provider: a[7],
              status: a[8],
              receipt_number: a[9],
              verified_at_ms: a[10],
              created_at_ms: a[11]
            });
          } else if (sql.includes("UPDATE certification_payments SET status = ?")) {
            const [status, receiptNumber, verifiedAtMs, txRef, uid] = this._args;
            const p = tables.certification_payments.get(txRef);
            if (p && p.user_id === uid) {
              p.status = status;
              p.receipt_number = receiptNumber;
              p.verified_at_ms = verifiedAtMs;
            }
          } else if (sql.includes("INSERT INTO notifications")) {
            const a = this._args;
            tables.notifications.unshift({
              id: a[0], userId: a[1], senderUid: a[2], senderName: a[3], senderAvatar: a[4],
              category: a[5], type: a[6], title: a[7], body: a[8],
              refId: a[9], refType: a[10], subRefId: a[11], dedupKey: a[12], priority: a[13],
              isRead: 0, readAtMs: 0, createdAtMs: a[14]
            });
          } else if (sql.includes("UPDATE notifications SET is_read = 1") && sql.includes("WHERE user_id = ? AND id IN")) {
            const [now, uid, ...ids] = this._args;
            const set = new Set(ids);
            tables.notifications.forEach((n) => {
              if (n.userId === uid && set.has(n.id)) { n.isRead = 1; n.readAtMs = now; }
            });
          } else if (sql.includes("UPDATE notifications SET is_read = 1") && sql.includes("AND category = ?")) {
            const [now, uid, cat] = this._args;
            tables.notifications.forEach((n) => {
              if (n.userId === uid && n.category === cat) { n.isRead = 1; n.readAtMs = now; }
            });
          } else if (sql.includes("UPDATE notifications SET is_read = 1")) {
            const [now, uid] = this._args;
            tables.notifications.forEach((n) => {
              if (n.userId === uid) { n.isRead = 1; n.readAtMs = now; }
            });
          } else if (sql.includes("DELETE FROM notifications WHERE id = ? AND user_id = ?")) {
            const [id, uid] = this._args;
            tables.notifications = tables.notifications.filter((n) => !(n.id === id && n.userId === uid));
          }
          return { success: true };
        }
      };
    }
  };
}

function createMockR2() {
  const objects = new Map();
  return {
    _objects: objects,
    async put(key, data, opts = {}) {
      const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);
      objects.set(key, {
        key,
        body: bytes,
        size: bytes.byteLength,
        httpEtag: `"etag-${key}"`,
        httpMetadata: opts.httpMetadata || {},
        customMetadata: opts.customMetadata || {},
        writeHttpMetadata(headers) {
          if (opts.httpMetadata?.contentType) {
            headers.set("Content-Type", opts.httpMetadata.contentType);
          }
        }
      });
    },
    async head(key) {
      return objects.get(key) ?? null;
    },
    async get(key) {
      return objects.get(key) ?? null;
    }
  };
}

function makeEnv(overrides = {}) {
  const queueMessages = [];
  return {
    FIREBASE_PROJECT_ID: "brav-chat",
    AI_GATEWAY_ID: "brav-chat-gateway",
    AI_DAILY_USER_QUOTA: "200",
    KV: createMockKv(),
    DB: createMockDb(),
    R2: createMockR2(),
    TASKS_QUEUE: { async send(msg) { queueMessages.push(msg); } },
    USER_HUBS: { idFromName: (n) => n, get: () => ({ fetch: async () => new Response(JSON.stringify({ delivered: 1 })) }) },
    CALL_ROOMS: { idFromName: (n) => n, get: () => ({ fetch: async () => new Response(null, { status: 426 }) }) },
    AI: {
      async run(model) {
        return { response: `OK:${model}` };
      }
    },
    __TEST_AUTH_VERIFIER: (token) => {
      if (token === "valid-user") return { uid: "u_test_1", email: "alice@bravchat.com", emailVerified: true };
      if (token === "other-user") return { uid: "u_other_2", email: "bob@bravchat.com", emailVerified: true };
      if (token === "designer-verified") return { uid: "u_designer", email: "bravoboutique225@gmail.com", emailVerified: true };
      if (token === "designer-unverified") return { uid: "u_spoof", email: "bravoboutique225@gmail.com", emailVerified: false };
      return null;
    },
    _queueMessages: queueMessages,
    ...overrides
  };
}

test("FREE_AI_MODELS removes legacy software references and keeps Cloudflare models", () => {
  const serialized = JSON.stringify(FREE_AI_MODELS);
  for (const forbidden of ["Mathematica", "Maple", "Maxima", "Xcas", "SageMath", "MATLAB", "Scilab", "Octave"]) {
    assert.equal(serialized.includes(forbidden), false, `Forbidden token ${forbidden} found in models`);
  }
  assert.ok(FREE_AI_MODELS.length >= 15);
  assert.ok(FREE_AI_MODELS.every((m) => m.id.startsWith("@cf/")));
});

test("selectModelForIntent selects specialized Cloudflare models automatically", () => {
  assert.equal(selectModelForIntent("latex_math", "\\int_0^1 x^2 dx", "auto"), "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b");
  assert.equal(selectModelForIntent("document", "Génère un rapport PDF", "auto"), "@cf/meta/llama-3.3-70b-instruct-fp8-fast");
  assert.equal(selectModelForIntent("auto_reply", "Allo?", "auto"), "@cf/meta/llama-3.1-8b-instruct");
  assert.equal(selectModelForIntent("", "Calcule \\frac{d}{dx} \\sin(x) en LaTeX", ""), "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b");
});

test("runCloudflareAiWithFallback trips circuit breaker on failing model and passes AI Gateway option", async () => {
  const seenCalls = [];
  const env = makeEnv({
    AI: {
      async run(model, input, opts) {
        seenCalls.push({ model, opts });
        if (model === "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b") {
          throw new Error("MODEL_RATE_LIMITED");
        }
        return { response: "Résultat $$x^3/3$$" };
      }
    }
  });
  const res = await runCloudflareAiWithFallback(env, "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b", [{ role: "user", content: "int x^2" }], 512, "latex_math");
  assert.equal(res.model, "@cf/qwen/qwq-32b");
  assert.equal(res.gatewayUsed, true);
  assert.equal(seenCalls[0].opts.gateway.id, "brav-chat-gateway");
});

test("/v1/config exposes v4.0.0 capabilities without leaking secrets", async () => {
  const env = makeEnv({ SFU_API_TOKEN: "super-secret-sfu", TURN_API_TOKEN: "super-secret-turn", TURN_TOKEN_ID: "turn-id" });
  const res = await worker.fetch(new Request("https://bold-darkness-7957.kolopay-ci.workers.dev/v1/config"), env);
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.version, "4.0.0");
  assert.equal(body.features.kv, true);
  assert.equal(body.features.queues, true);
  assert.equal(body.features.aiGateway, true);
  assert.equal(body.features.latexMath, true);
  const text = JSON.stringify(body);
  assert.equal(text.includes("super-secret"), false);
});

test("/v1/ai/math uses universal LaTeX engine and /v1/ai/auto-reply supports tone and history", async () => {
  let lastSystemPrompt = "";
  const env = makeEnv({
    AI: {
      async run(model, payload) {
        lastSystemPrompt = payload.messages[0].content;
        return { response: "$$\\frac{x^3}{3}$$" };
      }
    }
  });

  const mathRes = await worker.fetch(new Request("https://worker.local/v1/ai/math", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ expression: "\\int x^2 dx", model: "auto" })
  }), env);
  assert.equal(mathRes.status, 200);
  const mathJson = await mathRes.json();
  assert.equal(mathJson.sourceTag, "AI_ESTIMATED");
  assert.ok(lastSystemPrompt.includes("LaTeX"));
  assert.equal(lastSystemPrompt.includes("MATLAB"), false);

  const autoRes = await worker.fetch(new Request("https://worker.local/v1/ai/auto-reply", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      channel: "sms",
      senderName: "Koffi",
      incomingMessage: "On se voit à 15h ?",
      tone: "professionnel",
      recentReplies: ["D'accord pour 15h."]
    })
  }), env);
  assert.equal(autoRes.status, 200);
  const autoJson = await autoRes.json();
  assert.equal(autoJson.tone, "professionnel");
});

test("/v1/auto-reply/rules and /v1/launcher/profile persist cleanly in D1 + KV", async () => {
  const env = makeEnv();
  const postRule = await worker.fetch(new Request("https://worker.local/v1/auto-reply/rules", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ contactKey: "+2250701020304", contactLabel: "Awa", mode: "validate", tone: "amical" })
  }), env);
  assert.equal(postRule.status, 200);

  const getRules = await worker.fetch(new Request("https://worker.local/v1/auto-reply/rules", {
    headers: { Authorization: "Bearer valid-user" }
  }), env);
  const rulesJson = await getRules.json();
  assert.equal(rulesJson.rules.length, 1);
  assert.equal(rulesJson.rules[0].mode, "validate");

  const saveLauncher = await worker.fetch(new Request("https://worker.local/v1/launcher/profile", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ themeId: "brav_signature", iconShape: "squircle", configJson: "{\"dock\":[\"bravchat.com\"]}" })
  }), env);
  assert.equal(saveLauncher.status, 200);
});

test("AI enforces French-only replies, strips <think> tags, and auto-translates leaked English", async () => {
  assert.equal(stripAiReasoningTags("<think>Okay let me calculate...</think>Voici le résultat."), "Voici le résultat.");
  assert.equal(looksPredominantlyEnglish("Hello, here is the solution to your equation and the final answer."), true);
  assert.equal(looksPredominantlyEnglish("Bonjour, voici la solution exacte : $$\\frac{x^3}{3}$$"), false);

  const calls = [];
  const env = makeEnv({
    AI: {
      async run(model, payload) {
        calls.push(payload.messages);
        if (calls.length === 1) {
          return { response: "<think>Think in English</think>Hello, here is your answer for the integral." };
        }
        return { response: "Bonjour, voici la réponse en français pour votre intégrale." };
      }
    }
  });

  const res = await worker.fetch(new Request("https://worker.local/v1/ai/chat", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ prompt: "Hello, help me with math", model: "auto" })
  }), env);
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(calls[0][0].content.includes("OBLIGATOIREMENT répondre uniquement en français"), true);
  assert.equal(data.reply, "Bonjour, voici la réponse en français pour votre intégrale.");
});

test("/v1/ai/math includes pedagogical step-by-step and vector instructions", async () => {
  let capturedPrompt = "";
  const env = makeEnv({
    AI: {
      async run(model, payload) {
        capturedPrompt = payload.messages[0].content;
        return {
          response:
            "1. Énoncé : Addition des vecteurs u et v.\n" +
            "2. Données : Vecteur u : (2 ; 3 ; 1), Vecteur v : (4 ; 1 ; 2).\n" +
            "3. Résultat : w = (6 ; 4 ; 3)."
        };
      }
    }
  });
  const res = await worker.fetch(new Request("https://worker.local/v1/ai/math", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ expression: "Additionner u=(2;3;1) et v=(4;1;2)" })
  }), env);
  assert.equal(res.status, 200);
  assert.equal(capturedPrompt.includes("DIRECTIVE PÉDAGOGIQUE MATHÉMATIQUE"), true);
  assert.equal(capturedPrompt.includes("coordonnées"), true);
});

test("CERTIFICATION: 5000 followers gate, live camera selfie only, private R2 ACL, idempotent 3600 XOF payment, and designer JWT exemption", async () => {
  const env = makeEnv();
  const dummyJpegB64 = Buffer.from("JFIF-LIVE-SELFIE-IMAGE-" + "A".repeat(160)).toString("base64");

  // 1) Verified designer email bypasses followers, fee, and selfie strictly via verified JWT
  const designerElig = await worker.fetch(new Request("https://worker.local/v1/verification/eligibility", {
    headers: { Authorization: "Bearer designer-verified" }
  }), env);
  assert.equal(designerElig.status, 200);
  const designerJson = await designerElig.json();
  assert.equal(designerJson.exemptDesigner, true);
  assert.equal(designerJson.status, "verified");
  assert.equal(designerJson.feeXof, 0);

  // Unverified designer email MUST NOT get exemptDesigner
  const spoofElig = await worker.fetch(new Request("https://worker.local/v1/verification/eligibility", {
    headers: { Authorization: "Bearer designer-unverified" }
  }), env);
  assert.equal(spoofElig.status, 200);
  const spoofJson = await spoofElig.json();
  assert.equal(spoofJson.exemptDesigner, false);
  assert.equal(spoofJson.eligible, false);
  assert.equal(spoofJson.missingFollowers, 5000);

  // 2) Regular user with < 5000 followers is blocked from selfie submission and payment
  await worker.fetch(new Request("https://worker.local/v1/follows/toggle", {
    method: "POST",
    headers: { Authorization: "Bearer other-user", "Content-Type": "application/json" },
    body: JSON.stringify({ targetUid: "u_test_1", follow: true })
  }), env);
  const countRes = await worker.fetch(new Request("https://worker.local/v1/follows/count/u_test_1", {
    headers: { Authorization: "Bearer valid-user" }
  }), env);
  const countJson = await countRes.json();
  assert.equal(countJson.followersCount, 1);

  const blockedSelfie = await worker.fetch(new Request("https://worker.local/v1/verification/selfie", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      termsAccepted: true,
      selfieConsent: true,
      liveCameraOnly: true,
      galleryImportBlocked: true,
      faceCount: 1,
      livenessScore: 0.9,
      sharpnessScore: 0.8,
      luminance: 130,
      spoofRisk: "low",
      imageBase64: dummyJpegB64
    })
  }), env);
  assert.equal(blockedSelfie.status, 403);
  const blockedJson = await blockedSelfie.json();
  assert.equal(blockedJson.error, "INSUFFICIENT_FOLLOWERS");

  // 3) Populate 5000 followers for u_test_1 in D1
  for (let i = 0; i < 5000; i++) {
    env.DB._tables.user_follows.set(`u_test_1:f_${i}`, {
      target_uid: "u_test_1",
      follower_uid: `f_${i}`,
      created_at_ms: 1750000000000
    });
  }
  await env.KV.put("follows:count:u_test_1", JSON.stringify({ followersCount: 5000 }));

  // Gallery upload is rejected
  const galleryAttempt = await worker.fetch(new Request("https://worker.local/v1/verification/selfie", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      termsAccepted: true,
      selfieConsent: true,
      liveCameraOnly: false,
      galleryImportBlocked: false,
      faceCount: 1,
      livenessScore: 0.9,
      sharpnessScore: 0.8,
      luminance: 130,
      imageBase64: dummyJpegB64
    })
  }), env);
  assert.equal(galleryAttempt.status, 403);

  // Bad liveness (0 faces) triggers retake_required (422)
  const badFace = await worker.fetch(new Request("https://worker.local/v1/verification/selfie", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      termsAccepted: true,
      selfieConsent: true,
      liveCameraOnly: true,
      galleryImportBlocked: true,
      faceCount: 0,
      livenessScore: 0.1,
      sharpnessScore: 0.7,
      luminance: 130,
      imageBase64: dummyJpegB64
    })
  }), env);
  assert.equal(badFace.status, 422);

  // Valid live camera selfie stores private R2 object and advances to awaiting_payment
  const goodSelfie = await worker.fetch(new Request("https://worker.local/v1/verification/selfie", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      category: "creator",
      termsAccepted: true,
      selfieConsent: true,
      liveCameraOnly: true,
      galleryImportBlocked: true,
      faceCount: 1,
      livenessScore: 0.92,
      sharpnessScore: 0.81,
      luminance: 135,
      spoofRisk: "low",
      imageBase64: dummyJpegB64
    })
  }), env);
  assert.equal(goodSelfie.status, 200);
  const selfieJson = await goodSelfie.json();
  assert.equal(selfieJson.status, "awaiting_payment");
  assert.ok(selfieJson.selfieKey.startsWith("private/verif/u_test_1/"));

  // Private R2 ACL blocks other users (403) and allows owner (200)
  const otherMediaRead = await worker.fetch(new Request(`https://worker.local/v1/media/${selfieJson.selfieKey}`, {
    headers: { Authorization: "Bearer other-user" }
  }), env);
  assert.equal(otherMediaRead.status, 403);
  const ownerMediaRead = await worker.fetch(new Request(`https://worker.local/v1/media/${selfieJson.selfieKey}`, {
    headers: { Authorization: "Bearer valid-user" }
  }), env);
  assert.equal(ownerMediaRead.status, 200);

  // 4) Idempotent payment initiation for 3 600 F CFA (5,99 USD)
  const init1 = await worker.fetch(new Request("https://worker.local/v1/verification/payment/initiate", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ idempotencyKey: "idem-key-1", currency: "XOF" })
  }), env);
  assert.equal(init1.status, 201);
  const tx1 = await init1.json();
  assert.equal(tx1.amountXof, 3600);
  assert.equal(tx1.amountUsd, "5,99 USD");

  const init2 = await worker.fetch(new Request("https://worker.local/v1/verification/payment/initiate", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ idempotencyKey: "idem-key-1", currency: "XOF" })
  }), env);
  const tx2 = await init2.json();
  assert.equal(tx2.txRef, tx1.txRef);
  assert.equal(tx2.idempotentReplay, true);

  // Cancelled payment does not grant badge
  const cancelled = await worker.fetch(new Request("https://worker.local/v1/verification/payment/confirm", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ txRef: tx1.txRef, outcome: "cancelled" })
  }), env);
  assert.equal(cancelled.status, 402);
  const cancelledJson = await cancelled.json();
  assert.equal(cancelledJson.badgeGranted, false);

  // Initiate fresh tx and confirm with wrong amount -> rejected
  const init3 = await worker.fetch(new Request("https://worker.local/v1/verification/payment/initiate", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ idempotencyKey: "idem-key-2", currency: "XOF" })
  }), env);
  const tx3 = await init3.json();
  const wrongAmount = await worker.fetch(new Request("https://worker.local/v1/verification/payment/confirm", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ txRef: tx3.txRef, outcome: "confirmed", amountXof: 100 })
  }), env);
  assert.equal(wrongAmount.status, 400);

  // Confirm exact 3 600 F CFA -> badge granted + REC-BRV- electronic receipt
  const confirmed = await worker.fetch(new Request("https://worker.local/v1/verification/payment/confirm", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ txRef: tx3.txRef, outcome: "confirmed", amountXof: 3600, currency: "XOF" })
  }), env);
  assert.equal(confirmed.status, 200);
  const confirmedJson = await confirmed.json();
  assert.equal(confirmedJson.badgeGranted, true);
  assert.equal(confirmedJson.status, "verified");
  assert.ok(confirmedJson.receipt.receiptNumber.startsWith("REC-BRV-"));
});

test("Centralized Notifications: emit with dedup, block spoofed security alerts, filter, mark read/read-all, and delete", async () => {
  const env = makeEnv();

  // Another user cannot emit a spoofed account_security notification to Alice
  const spoofSec = await worker.fetch(new Request("https://worker.local/v1/notifications/emit", {
    method: "POST",
    headers: { Authorization: "Bearer other-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      targetUid: "u_test_1",
      category: "account_security",
      title: "Fake alert",
      body: "Spoof"
    })
  }), env);
  assert.equal(spoofSec.status, 403);

  // Bob emits an ACTUS like notification to Alice with dedupKey
  const emit1 = await worker.fetch(new Request("https://worker.local/v1/notifications/emit", {
    method: "POST",
    headers: { Authorization: "Bearer other-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      targetUid: "u_test_1",
      senderName: "Bob",
      category: "actus",
      type: "actus_like",
      title: "Nouveau J'aime sur ACTUS",
      body: "Bob a aimé votre publication.",
      refId: "post_1",
      refType: "actus",
      dedupKey: "like:post_1:u_other_2"
    })
  }), env);
  assert.equal(emit1.status, 201);
  const e1 = await emit1.json();
  assert.equal(e1.duplicate, false);

  // Duplicate event is deduplicated cleanly
  const emitDup = await worker.fetch(new Request("https://worker.local/v1/notifications/emit", {
    method: "POST",
    headers: { Authorization: "Bearer other-user", "Content-Type": "application/json" },
    body: JSON.stringify({
      targetUid: "u_test_1",
      senderName: "Bob",
      category: "actus",
      type: "actus_like",
      title: "Nouveau J'aime sur ACTUS",
      body: "Bob a aimé votre publication.",
      refId: "post_1",
      refType: "actus",
      dedupKey: "like:post_1:u_other_2"
    })
  }), env);
  const eDup = await emitDup.json();
  assert.equal(eDup.duplicate, true);

  // List notifications for Alice
  const listRes = await worker.fetch(new Request("https://worker.local/v1/notifications?category=actus", {
    headers: { Authorization: "Bearer valid-user" }
  }), env);
  assert.equal(listRes.status, 200);
  const listJson = await listRes.json();
  assert.equal(listJson.notifications.length, 1);
  assert.equal(listJson.unreadCount, 1);
  const notifId = listJson.notifications[0].id;

  // Mark read + verify unreadCount = 0
  await worker.fetch(new Request("https://worker.local/v1/notifications/read", {
    method: "POST",
    headers: { Authorization: "Bearer valid-user", "Content-Type": "application/json" },
    body: JSON.stringify({ ids: [notifId] })
  }), env);
  const listAfterRead = await (await worker.fetch(new Request("https://worker.local/v1/notifications", {
    headers: { Authorization: "Bearer valid-user" }
  }), env)).json();
  assert.equal(listAfterRead.unreadCount, 0);

  // Delete notification
  await worker.fetch(new Request(`https://worker.local/v1/notifications/${notifId}`, {
    method: "DELETE",
    headers: { Authorization: "Bearer valid-user" }
  }), env);
  const listAfterDel = await (await worker.fetch(new Request("https://worker.local/v1/notifications", {
    headers: { Authorization: "Bearer valid-user" }
  }), env)).json();
  assert.equal(listAfterDel.notifications.length, 0);
});
