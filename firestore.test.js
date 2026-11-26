const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-bravchat-a59c08fd-d746-4b1b-82b9-b5b35a581a65";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const EVE_UID = "eve_uid_3";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ts(context) {
  return context.firestore().app ? new Date() : new Date();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read users, conversations, calls, statuses, or live_locations", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(db.collection("users").get());
  await assertFails(db.collection("conversations").get());
  await assertFails(db.collection("calls").get());
  await assertFails(db.collection("statuses").get());
  await assertFails(db.collection("live_locations").get());
});

test("PII Isolation: Bob cannot read or write Alice's /users/{uid}/private/info or devices or prefs", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      phoneNumber: "+2250700000001",
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversation_prefs").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
});

test("User cannot self-suspend or escalate role on create, and cannot modify isSuspended on update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      isSuspended: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      isSuspended: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isSuspended: true,
      updatedAt: new Date(),
    })
  );
});

test("Conversations and Messages: Participants only, Eve blocked, reactions allowed for participants", async () => {
  const convId = `conv_${ALICE_UID}_${BOB_UID}`;
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participants: [ALICE_UID, BOB_UID],
      isGroup: false,
      lastMessage: "Salut",
      lastMessageSenderId: ALICE_UID,
      lastMessageType: "text",
      updatedAt: new Date(),
    })
  );

  await assertFails(eveDb.collection("conversations").doc(convId).get());
  await assertFails(
    eveDb.collection("conversations").doc(convId).collection("messages").doc("m1").set({
      conversationId: convId,
      senderId: EVE_UID,
      receiverId: ALICE_UID,
      content: "Intrusion",
      messageType: "text",
      status: "sent",
      timestamp: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("m1").set({
      conversationId: convId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      content: "Bonjour Bob!",
      messageType: "text",
      status: "sent",
      timestamp: new Date(),
    })
  );

  // Bob can react and mark read
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("m1").update({
      reactions: { heart_pulse: [BOB_UID] },
    })
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("m1").update({
      status: "read",
      readAt: new Date(),
    })
  );
});

test("Calls: Only participants can access, terminal status 'ended' locks further edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "calling",
      createdAt: new Date(),
    })
  );

  await assertFails(eveDb.collection("calls").doc("call_1").get());
  await assertSucceeds(bobDb.collection("calls").where("receiverId", "==", BOB_UID).get());
  await assertSucceeds(aliceDb.collection("calls").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(eveDb.collection("calls").where("participantIds", "array-contains", ALICE_UID).get());
  await assertSucceeds(bobDb.collection("calls").doc("call_1").update({ status: "accepted" }));
  await assertSucceeds(aliceDb.collection("calls").doc("call_1").update({ status: "ended", durationSeconds: 12 }));
  await assertFails(bobDb.collection("calls").doc("call_1").update({ status: "accepted" }));
});

test("Live Locations: Only owner and listed recipients can read encrypted location", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("live_locations").doc("loc_alice_1").set({
      ownerId: ALICE_UID,
      ownerName: "Alice",
      recipientUids: [BOB_UID],
      encryptedCoords: "base64cipher==",
      encryptionIv: "iv123",
      senderPublicKey: "pubKey",
      isEncrypted: true,
      active: true,
      expiresAtMs: Date.now() + 3600000,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(bobDb.collection("live_locations").doc("loc_alice_1").get());
  await assertFails(eveDb.collection("live_locations").doc("loc_alice_1").get());
});

test("Message 30s edit window: allowed <= 30s, blocked > 30s, deleteForAll allowed anytime", async () => {
  const convId = `conv_${ALICE_UID}_${BOB_UID}`;
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participants: [ALICE_UID, BOB_UID],
      isGroup: false,
      lastMessage: "Salut",
      lastMessageSenderId: ALICE_UID,
      lastMessageType: "text",
      updatedAt: new Date(),
    })
  );

  // Recent message (within 30s) -> edit allowed
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("m_recent").set({
      conversationId: convId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      content: "Original",
      messageType: "text",
      status: "sent",
      timestamp: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("m_recent").update({
      content: "Modifié < 30s",
      isEncrypted: false,
      encryptionIv: "",
      editedAt: new Date(),
    })
  );

  // Old message (35s ago) -> content edit MUST FAIL, deleteForAll MUST SUCCEED
  const oldTs = new Date(Date.now() - 35_000);
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("m_old").set({
      conversationId: convId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      content: "Trop tard",
      messageType: "text",
      status: "sent",
      timestamp: oldTs,
    })
  );
  await assertFails(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("m_old").update({
      content: "Tentative tardive",
      isEncrypted: false,
      encryptionIv: "",
      editedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("m_old").update({
      deletedForAll: true,
      content: "Ce message a été supprimé",
      isEncrypted: false,
      encryptionIv: "",
      editedAt: new Date(),
    })
  );
});

test("Conversation deletion: participant can delete messages and conversation, Eve blocked", async () => {
  const convId = `conv_${ALICE_UID}_${BOB_UID}`;
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participants: [ALICE_UID, BOB_UID],
      isGroup: false,
      lastMessage: "Hello",
      lastMessageSenderId: ALICE_UID,
      lastMessageType: "text",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("m_del").set({
      conversationId: convId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      content: "Msg",
      messageType: "text",
      status: "sent",
      timestamp: new Date(),
    })
  );

  await assertFails(eveDb.collection("conversations").doc(convId).collection("messages").doc("m_del").delete());
  await assertFails(eveDb.collection("conversations").doc(convId).delete());

  await assertSucceeds(bobDb.collection("conversations").doc(convId).collection("messages").doc("m_del").delete());
  await assertSucceeds(bobDb.collection("conversations").doc(convId).delete());
});

test("Free Offers: only published+free offers allowed, pending_payment rejected, owner-only delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  await assertFails(
    aliceDb.collection("offers").doc("off_paid_attempt").set({
      userId: ALICE_UID,
      authorName: "Alice",
      title: "Annonce payante",
      category: "Services",
      description: "Test",
      status: "pending_payment",
      paymentStatus: "pending",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("offers").doc("off_free_1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      offerType: "product",
      title: "Ordinateur portable Pro",
      category: "Vente",
      condition: "Comme neuf",
      description: "Entraide développeurs",
      locationCity: "Abidjan",
      priceOrSalary: "350 000 FCFA",
      contactInfo: "+2250700000001",
      imageUrl: "https://example.com/pc1.jpg",
      imageUrls: ["https://example.com/pc1.jpg", "https://example.com/pc2.jpg"],
      status: "published",
      paymentStatus: "free",
      createdAt: new Date(),
    })
  );

  // Owner can edit product images and price, non-owner cannot edit or delete
  await assertSucceeds(
    aliceDb.collection("offers").doc("off_free_1").update({
      priceOrSalary: "320 000 FCFA",
      imageUrls: ["https://example.com/pc2.jpg"],
      imageUrl: "https://example.com/pc2.jpg",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("offers").doc("off_free_1").update({
      priceOrSalary: "1 FCFA",
      updatedAt: new Date(),
    })
  );
  await assertFails(bobDb.collection("offers").doc("off_free_1").delete());
  await assertSucceeds(aliceDb.collection("offers").doc("off_free_1").delete());
});

test("Call WebRTC signals: only call participants can write/read SDP & ICE signals, Eve blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_webrtc_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "calling",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_webrtc_1").collection("signals").doc("sig_offer_1").set({
      senderId: ALICE_UID,
      type: "offer",
      payload: JSON.stringify({ type: "offer", sdp: { sdp: "v=0..." } }),
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    bobDb.collection("calls").doc("call_webrtc_1").collection("signals").doc("sig_offer_1").get()
  );

  await assertFails(
    eveDb.collection("calls").doc("call_webrtc_1").collection("signals").doc("sig_offer_1").get()
  );
  await assertFails(
    eveDb.collection("calls").doc("call_webrtc_1").collection("signals").doc("sig_intrusion").set({
      senderId: EVE_UID,
      type: "offer",
      payload: "{}",
      createdAt: new Date(),
    })
  );
});

test("Call history deletion: participant can hide ended call via hiddenForUsers, Eve blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_hist_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "audio",
      status: "calling",
      hiddenForUsers: [],
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("calls").doc("call_hist_1").update({ status: "ended", durationSeconds: 25 }));

  // Alice hides the call from her own history
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_hist_1").update({ hiddenForUsers: [ALICE_UID] })
  );
  // Bob cannot un-hide Alice (must preserve existing hiddenForUsers)
  await assertFails(
    bobDb.collection("calls").doc("call_hist_1").update({ hiddenForUsers: [BOB_UID] })
  );
  // Bob can append himself
  await assertSucceeds(
    bobDb.collection("calls").doc("call_hist_1").update({ hiddenForUsers: [ALICE_UID, BOB_UID] })
  );
  // Eve cannot touch it
  await assertFails(
    eveDb.collection("calls").doc("call_hist_1").update({ hiddenForUsers: [ALICE_UID, BOB_UID, EVE_UID] })
  );
});

test("Statuses: viewers can like, react with futuristic emojis, and comment; selected audience blocks Eve", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("statuses").doc("st_sel_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      type: "text",
      text: "Statut futuriste privé!",
      audienceType: "selected",
      allowedUids: [BOB_UID],
      viewerIds: [],
      likedByUids: [],
      reactions: {},
      commentCount: 0,
      expiresAtMs: Date.now() + 86400000,
      createdAt: new Date(),
    })
  );

  // Bob is allowed: can like, react, and post emoji comment
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_sel_1").update({
      likedByUids: [BOB_UID],
      reactions: { cyber_bot: [BOB_UID] },
    })
  );
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_sel_1").collection("comments").doc("cmt_1").set({
      statusId: "st_sel_1",
      authorId: BOB_UID,
      authorName: "Bob",
      text: "Incroyable ce statut !",
      reactionKey: "rocket_turbo",
      createdAt: new Date(),
    })
  );
  // Emoji-only comment also allowed
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_sel_1").collection("comments").doc("cmt_2").set({
      statusId: "st_sel_1",
      authorId: BOB_UID,
      authorName: "Bob",
      text: "",
      reactionKey: "clown_mode",
      createdAt: new Date(),
    })
  );

  // Eve is NOT in allowedUids -> denied read, like, and comment
  await assertFails(eveDb.collection("statuses").doc("st_sel_1").get());
  await assertFails(eveDb.collection("statuses").doc("st_sel_1").update({ likedByUids: [EVE_UID] }));
  await assertFails(
    eveDb.collection("statuses").doc("st_sel_1").collection("comments").doc("cmt_eve").set({
      statusId: "st_sel_1",
      authorId: EVE_UID,
      authorName: "Eve",
      text: "Intrusion",
      createdAt: new Date(),
    })
  );
});

test("Statuses Video Reels: metadata, viewCount, bookmark, shareCount allowed; allowComments=false blocks comments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("statuses").doc("st_vid_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      type: "video",
      text: "",
      caption: "Danse Abidjan #culture",
      mediaUrl: "https://worker.example/v1/media/c/status_alice/clip.mp4",
      mediaUrls: ["https://worker.example/v1/media/c/status_alice/clip.mp4"],
      mediaAspectRatio: 0.5625,
      mediaWidth: 1080,
      mediaHeight: 1920,
      mediaAspectRatios: [0.5625],
      thumbnailUrl: "https://worker.example/v1/media/c/status_alice/thumb.jpg",
      durationMs: 14500,
      allowComments: false,
      locationLabel: "Abidjan",
      hashtags: ["#culture"],
      mentions: [],
      audienceType: "all",
      allowedUids: [],
      viewerIds: [],
      likedByUids: [],
      bookmarkedByUids: [],
      reactions: {},
      commentCount: 0,
      viewCount: 0,
      shareCount: 0,
      expiresAtMs: Date.now() + 86400000000,
      createdAt: new Date(),
    })
  );

  // Viewer can update viewCount, bookmarkedByUids, and shareCount
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_vid_1").update({
      viewerIds: [BOB_UID],
      viewCount: 1,
      bookmarkedByUids: [BOB_UID],
      shareCount: 1,
    })
  );

  // Viewer cannot mutate caption or allowComments
  await assertFails(
    bobDb.collection("statuses").doc("st_vid_1").update({
      caption: "Hacked",
    })
  );

  // Comments blocked when allowComments is false
  await assertFails(
    bobDb.collection("statuses").doc("st_vid_1").collection("comments").doc("cmt_blocked").set({
      statusId: "st_vid_1",
      authorId: BOB_UID,
      authorName: "Bob",
      text: "Commentaire interdit",
      createdAt: new Date(),
    })
  );
});

test("Calls: caller/receiver avatars, on_hold/heldByUid, and hold/resume signals work for participants only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_hold_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      callerAvatar: "https://example.com/a.jpg",
      callerUsername: "alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      receiverAvatar: "https://example.com/b.jpg",
      receiverUsername: "bob",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "audio",
      status: "calling",
      heldByUid: "",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(bobDb.collection("calls").doc("call_hold_1").update({ status: "accepted" }));
  await assertSucceeds(aliceDb.collection("calls").doc("call_hold_1").update({ status: "on_hold", heldByUid: ALICE_UID }));
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_hold_1").collection("signals").doc("sig_hold").set({
      senderId: ALICE_UID,
      type: "hold",
      payload: JSON.stringify({ held: true }),
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("calls").doc("call_hold_1").update({ status: "accepted", heldByUid: "" }));
  await assertFails(eveDb.collection("calls").doc("call_hold_1").update({ status: "on_hold", heldByUid: EVE_UID }));
});

test("Certification: users cannot self-verify; verification_requests and badges require admin review", async () => {
  const adminUid = "admin_brav_1";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("admins").doc(adminUid).set({ role: "admin" });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const adminDb = testEnv.authenticatedContext(adminUid).firestore(DATABASE_ID);

  // User cannot create user doc already verified
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      verificationStatus: "verified",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Normal create succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      verificationStatus: "none",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Alice cannot self-grant blue badge on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      verificationStatus: "verified",
      updatedAt: new Date(),
    })
  );

  // Alice submits live selfie verification request awaiting payment
  await assertSucceeds(
    aliceDb.collection("verification_requests").doc(`verif_${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Alice",
      fullName: "Alice Konan",
      category: "professional",
      organizationName: "Cabinet Konan",
      justification: "Certification automatisée BRAV VERIFIED",
      followersCountAtRequest: 0,
      selfieVerified: true,
      selfieScore: 0.91,
      spoofRisk: "low",
      selfieStorageKey: `private/verif/${ALICE_UID}/selfie.jpg`,
      selfieConsent: true,
      termsAccepted: true,
      status: "awaiting_payment",
      createdAt: new Date(),
    })
  );

  // Bob cannot read or approve Alice's request; Alice cannot self-grant verified with 0 followers
  await assertFails(bobDb.collection("verification_requests").doc(`verif_${ALICE_UID}`).get());
  await assertFails(
    aliceDb.collection("verification_requests").doc(`verif_${ALICE_UID}`).update({
      status: "verified",
      paymentStatus: "confirmed",
      paymentAmountXof: 3600,
      followersCountAtRequest: 5100,
      receiptNumber: "REC-BRV-FAKE1",
      updatedAt: new Date(),
    })
  );

  // Admin requests selfie retake
  await assertSucceeds(
    adminDb.collection("verification_requests").doc(`verif_${ALICE_UID}`).update({
      status: "retake_required",
      reviewerNote: "Merci de reprendre le selfie en direct.",
      reviewedBy: adminUid,
      auditTrail: ["Selfie initial", "Reprise demandée"],
      updatedAt: new Date(),
    })
  );

  // Alice can submit a new selfie -> awaiting_payment, still cannot set verified without 5000 followers
  await assertSucceeds(
    aliceDb.collection("verification_requests").doc(`verif_${ALICE_UID}`).update({
      selfieVerified: true,
      selfieScore: 0.94,
      spoofRisk: "low",
      status: "awaiting_payment",
      auditTrail: ["Selfie initial", "Reprise demandée", "Nouveau selfie validé"],
      updatedAt: new Date(),
    })
  );

  // Admin approves request & updates Alice's badge
  await assertSucceeds(
    adminDb.collection("verification_requests").doc(`verif_${ALICE_UID}`).update({
      status: "verified",
      reviewerNote: "Identité confirmée",
      reviewedBy: adminUid,
      auditTrail: ["Selfie initial", "Reprise demandée", "Nouveau selfie validé", "Approuvé"],
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      verificationStatus: "verified",
      verificationCategory: "professional",
      verifiedOrgName: "Cabinet Konan",
      updatedAt: new Date(),
    })
  );
});

test("follow graph counters prevent self-spoofing and verified designer email exemption works strictly via JWT", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      followersCount: 0,
      followingCount: 0,
      verificationStatus: "none",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      followersCount: 0,
      followingCount: 0,
      verificationStatus: "none",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob cannot arbitrarily inflate his own followersCount
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      followersCount: 5000,
    })
  );

  // Alice follows Bob via atomic batch
  const aliceFollowBatch = aliceDb.batch();
  aliceFollowBatch.set(aliceDb.collection("users").doc(BOB_UID).collection("followers").doc(ALICE_UID), {
    followerUid: ALICE_UID,
    createdAt: new Date(),
  });
  aliceFollowBatch.set(aliceDb.collection("users").doc(ALICE_UID).collection("following").doc(BOB_UID), {
    targetUid: BOB_UID,
    createdAt: new Date(),
  });
  aliceFollowBatch.update(aliceDb.collection("users").doc(BOB_UID), { followersCount: 1 });
  aliceFollowBatch.update(aliceDb.collection("users").doc(ALICE_UID), { followingCount: 1, updatedAt: new Date() });
  await assertSucceeds(aliceFollowBatch.commit());

  // Unverified designer email JWT cannot bypass badge gate
  const unverifiedDesignerDb = testEnv.authenticatedContext("designer_spoof", {
    email: "bravoboutique225@gmail.com",
    email_verified: false,
  }).firestore(DATABASE_ID);
  await assertFails(
    unverifiedDesignerDb.collection("users").doc("designer_spoof").set({
      userId: "designer_spoof",
      displayName: "Spoof",
      verificationStatus: "verified",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Verified designer email JWT grants automatic verified status without followers or fee
  const verifiedDesignerDb = testEnv.authenticatedContext("designer_real", {
    email: "bravoboutique225@gmail.com",
    email_verified: true,
  }).firestore(DATABASE_ID);
  await assertSucceeds(
    verifiedDesignerDb.collection("users").doc("designer_real").set({
      userId: "designer_real",
      displayName: "Concepteur BRAV",
      verificationStatus: "verified",
      verificationCategory: "creator",
      verifiedOrgName: "Concepteur BRAV CHAT",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("BRAV Call ID caller_reports: pending hidden from others, approved readable, admin-only moderation", async () => {
  const adminUid = "admin_brav_2";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("admins").doc(adminUid).set({ role: "admin" });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const adminDb = testEnv.authenticatedContext(adminUid).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("caller_reports").doc("crep_1").set({
      reporterId: ALICE_UID,
      phoneLookupHash: "abcdef0123456789abcdef0123456789",
      maskedNumber: "+22507••••89",
      countryIso: "CI",
      category: "telemarketing",
      label: "Démarchage bancaire",
      status: "pending",
      createdAt: new Date(),
    })
  );

  // Bob cannot read pending report; Alice cannot self-approve
  await assertFails(bobDb.collection("caller_reports").doc("crep_1").get());
  await assertFails(
    aliceDb.collection("caller_reports").doc("crep_1").update({ status: "approved" })
  );

  // Admin approves -> Bob can read approved reports
  await assertSucceeds(
    adminDb.collection("caller_reports").doc("crep_1").update({ status: "approved" })
  );
  await assertSucceeds(bobDb.collection("caller_reports").doc("crep_1").get());
});

test("Designer exemption heals stale verification_requests case-insensitively and notifications enforce zero-trust ACL", async () => {
  const designerUid = "designer_case_test";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("verification_requests").doc(`verif_${designerUid}`).set({
      userId: designerUid,
      fullName: "Designer",
      category: "creator",
      status: "not_eligible",
      designerExempt: false,
      createdAt: new Date(),
    });
  });

  const designerDb = testEnv.authenticatedContext(designerUid, {
    email: "Bravoboutique225@gmail.com",
    email_verified: true,
  }).firestore(DATABASE_ID);

  await assertSucceeds(
    designerDb.collection("verification_requests").doc(`verif_${designerUid}`).update({
      status: "verified",
      designerExempt: true,
      updatedAt: new Date(),
    })
  );

  // Notifications: Bob can notify Alice on ACTUS, cannot spoof account_security, cannot read Alice's notifications
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("ntf_1").set({
      userId: ALICE_UID,
      senderUid: BOB_UID,
      senderName: "Bob",
      category: "actus",
      type: "actus_like",
      title: "Nouveau J'aime",
      body: "Bob a aimé votre publication.",
      isRead: false,
      readAtMs: 0,
      createdAtMs: Date.now(),
      createdAt: new Date(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("ntf_sec").set({
      userId: ALICE_UID,
      senderUid: BOB_UID,
      category: "account_security",
      type: "security",
      title: "Fake",
      body: "Spoofed security alert",
      isRead: false,
      createdAt: new Date(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("ntf_1").update({
      isRead: true,
      readAtMs: Date.now(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("ntf_1").delete());
});

test("Private profile follow_requests, owner follower removal, anti-self-follow & anti-counter tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Privée",
      isPrivateProfile: true,
      followersCount: 0,
      followingCount: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Public",
      isPrivateProfile: false,
      followersCount: 0,
      followingCount: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob CANNOT bypass Alice's private profile by writing directly to Alice's followers
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("followers").doc(BOB_UID).set({
      followerUid: BOB_UID,
      createdAt: new Date(),
    })
  );

  // Self-follow forbidden
  await assertFails(
    bobDb.collection("follow_requests").doc(`${BOB_UID}_${BOB_UID}`).set({
      fromUid: BOB_UID,
      toUid: BOB_UID,
      status: "pending",
      createdAt: new Date(),
    })
  );

  // Bob sends a follow request to Alice; Eve cannot read it
  const reqId = `${BOB_UID}_${ALICE_UID}`;
  await assertSucceeds(
    bobDb.collection("follow_requests").doc(reqId).set({
      fromUid: BOB_UID,
      fromName: "Bob Public",
      toUid: ALICE_UID,
      toName: "Alice Privée",
      status: "pending",
      createdAt: new Date(),
    })
  );
  await assertFails(eveDb.collection("follow_requests").doc(reqId).get());

  // Bob cannot self-accept his own request
  await assertFails(
    bobDb.collection("follow_requests").doc(reqId).update({
      status: "accepted",
      updatedAt: new Date(),
    })
  );

  // Alice atomically accepts the request, adds Bob to followers & Bob's following, and updates counts
  const acceptBatch = aliceDb.batch();
  acceptBatch.update(aliceDb.collection("follow_requests").doc(reqId), {
    status: "accepted",
    updatedAt: new Date(),
  });
  acceptBatch.set(aliceDb.collection("users").doc(ALICE_UID).collection("followers").doc(BOB_UID), {
    followerUid: BOB_UID,
    createdAt: new Date(),
  });
  acceptBatch.set(aliceDb.collection("users").doc(BOB_UID).collection("following").doc(ALICE_UID), {
    targetUid: ALICE_UID,
    createdAt: new Date(),
  });
  acceptBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    followersCount: 1,
    updatedAt: new Date(),
  });
  acceptBatch.update(aliceDb.collection("users").doc(BOB_UID), {
    followingCount: 1,
  });
  await assertSucceeds(acceptBatch.commit());

  // Arbitrary counter inflation blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      followersCount: 9999,
      updatedAt: new Date(),
    })
  );

  // Alice (owner) can remove Bob from her followers
  const removeBatch = aliceDb.batch();
  removeBatch.delete(aliceDb.collection("users").doc(ALICE_UID).collection("followers").doc(BOB_UID));
  removeBatch.delete(aliceDb.collection("users").doc(BOB_UID).collection("following").doc(ALICE_UID));
  removeBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    followersCount: 0,
    updatedAt: new Date(),
  });
  removeBatch.update(aliceDb.collection("users").doc(BOB_UID), {
    followingCount: 0,
  });
  await assertSucceeds(removeBatch.commit());
});

test("Invite links & verified caller_directory rules enforce ACLs", async () => {
  const adminUid = "admin_brav_dir";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore(DATABASE_ID).collection("admins").doc(adminUid).set({ role: "admin" });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const adminDb = testEnv.authenticatedContext(adminUid).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("invites").doc("BRVALICE01").set({
      code: "BRVALICE01",
      inviterUid: ALICE_UID,
      inviterDisplayName: "Alice",
      channel: "whatsapp",
      clicks: 0,
      signups: 0,
      active: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob can read active invite and increment click by +1, cannot jump by +10
  await assertSucceeds(bobDb.collection("invites").doc("BRVALICE01").get());
  await assertSucceeds(
    bobDb.collection("invites").doc("BRVALICE01").update({
      clicks: 1,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("invites").doc("BRVALICE01").update({
      clicks: 11,
      updatedAt: new Date(),
    })
  );
  await assertFails(bobDb.collection("invites").doc("BRVALICE01").delete());

  // Caller directory: signed-in read allowed, non-admin write denied, admin write allowed
  const hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  await assertFails(
    aliceDb.collection("caller_directory").doc(hash).set({
      phoneLookupHash: hash,
      businessName: "Fake Bank",
      category: "business",
      city: "Abidjan",
      countryIso: "CI",
      verified: true,
    })
  );
  await assertSucceeds(
    adminDb.collection("caller_directory").doc(hash).set({
      phoneLookupHash: hash,
      businessName: "Banque Atlantique CI",
      category: "business",
      city: "Abidjan",
      countryIso: "CI",
      verified: true,
    })
  );
  await assertSucceeds(bobDb.collection("caller_directory").doc(hash).get());
});

