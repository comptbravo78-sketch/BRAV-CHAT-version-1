package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.crypto.E2eeCryptoManager
import com.example.model.User
import com.example.repository.CallRepository
import com.example.repository.ChatRepository
import com.example.repository.SocialRepository
import com.example.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class BravRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun userProfileAndPrivateInfo_ownerCanCreateAndUpdate_crossUserPrivateDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_user_${UUID.randomUUID().toString().take(6)}@test.com")
        val crypto = E2eeCryptoManager(ApplicationProvider.getApplicationContext())
        val userRepo = UserRepository(firestore, crypto)
        val aliceFb = checkNotNull(auth.currentUser)

        val ensured = withTimeout(TIMEOUT_MS) { userRepo.ensureUserProfileExists(aliceFb, "dev_test_1") }
        assertTrue("ensureUserProfileExists failed: ${ensured.exceptionOrNull()}", ensured.isSuccess)

        val updateRes = withTimeout(TIMEOUT_MS) {
            userRepo.updateProfile(
                uid = aliceUid,
                displayName = "Alice Brav",
                username = "alice_${aliceUid.take(5)}",
                bio = "Test bio",
                locationCity = "Abidjan",
                locationDetails = "Cocody",
                phoneNumber = "+2250701020304"
            )
        }
        assertTrue("updateProfile failed: ${updateRes.exceptionOrNull()}", updateRes.isSuccess)

        // Bob tries to read Alice's private info -> MUST fail with PERMISSION_DENIED
        signInTestUser("bob_user_${UUID.randomUUID().toString().take(6)}@test.com")
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("private").document("info")
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED for Bob reading Alice's private PII")
        } catch (e: Exception) {
            val ffe = checkNotNull(e.unwrapFirestoreException()) { "Expected Firestore exception, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
        }
    }

    @Test
    fun chatMessagingAndReactions_participantsWork_nonParticipantDenied() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("chat_alice_$suffix@test.com")
        val crypto = E2eeCryptoManager(ApplicationProvider.getApplicationContext())
        val userRepo = UserRepository(firestore, crypto)
        userRepo.ensureUserProfileExists(checkNotNull(auth.currentUser), "dev_a")
        val aliceUser = User(userId = aliceUid, displayName = "Alice", publicKey = crypto.getPublicKeyBase64(aliceUid))

        val bobUid = signInTestUser("chat_bob_$suffix@test.com")
        userRepo.ensureUserProfileExists(checkNotNull(auth.currentUser), "dev_b")
        val bobUser = User(userId = bobUid, displayName = "Bob", publicKey = crypto.getPublicKeyBase64(bobUid))

        // Sign back in as Alice and create direct conversation + E2EE message
        signInTestUser("chat_alice_$suffix@test.com")
        val chatRepo = ChatRepository(firestore, workerApiClient = null, cryptoManager = crypto)
        val convRes = withTimeout(TIMEOUT_MS) { chatRepo.getOrCreateDirectConversation(aliceUser, bobUser) }
        assertTrue("getOrCreateDirectConversation failed: ${convRes.exceptionOrNull()}", convRes.isSuccess)
        val conv = convRes.getOrThrow()

        val msgRes = withTimeout(TIMEOUT_MS) {
            chatRepo.sendTextMessage(conv, aliceUid, "Bonjour Bob sécurisé!", peerPublicKey = bobUser.publicKey)
        }
        assertTrue("sendTextMessage failed: ${msgRes.exceptionOrNull()}", msgRes.isSuccess)

        val msgs = withTimeout(TIMEOUT_MS) {
            chatRepo.observeMessages(conv.id).first { it.isNotEmpty() }
        }
        assertEquals(1, msgs.size)
        val decryptedAlice = chatRepo.decryptDisplayText(msgs.first(), aliceUid, conv)
        assertEquals("Bonjour Bob sécurisé!", decryptedAlice)

        // Bob reacts with HEART_PULSE
        signInTestUser("chat_bob_$suffix@test.com")
        val bobMsgs = withTimeout(TIMEOUT_MS) {
            chatRepo.observeMessages(conv.id).first { it.isNotEmpty() }
        }
        assertEquals("Bonjour Bob sécurisé!", chatRepo.decryptDisplayText(bobMsgs.first(), bobUid, conv))
        val reactRes = withTimeout(TIMEOUT_MS) {
            chatRepo.toggleReaction(conv.id, bobMsgs.first(), bobUid, "heart_pulse")
        }
        assertTrue("toggleReaction failed: ${reactRes.exceptionOrNull()}", reactRes.isSuccess)

        // Eve (outsider) cannot read conversation or messages
        signInTestUser("chat_eve_$suffix@test.com")
        try {
            withTimeout(TIMEOUT_MS) {
                chatRepo.observeMessages(conv.id).first()
            }
            fail("Expected PERMISSION_DENIED for Eve observing Alice/Bob messages")
        } catch (e: Exception) {
            val ffe = checkNotNull(e.unwrapFirestoreException()) { "Expected Firestore exception, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
        }
    }

    @Test
    fun callsStatusesLiveLocation_rulesAllowValidAndRejectUnauthenticated() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("social_alice_$suffix@test.com")
        val crypto = E2eeCryptoManager(ApplicationProvider.getApplicationContext())
        val callRepo = CallRepository(firestore, null)
        val socialRepo = SocialRepository(firestore, null, crypto)

        val bobUid = "bob_$suffix"
        val callRes = withTimeout(TIMEOUT_MS) {
            callRepo.startCall(aliceUid, "Alice", bobUid, "Bob", "video")
        }
        assertTrue("startCall failed: ${callRes.exceptionOrNull()}", callRes.isSuccess)
        val call = callRes.getOrThrow()
        val endRes = withTimeout(TIMEOUT_MS) {
            callRepo.updateCallStatus(call.id, "ended", missed = false, durationSeconds = 42L)
        }
        assertTrue("updateCallStatus failed: ${endRes.exceptionOrNull()}", endRes.isSuccess)

        val aliceUser = User(userId = aliceUid, displayName = "Alice", publicKey = crypto.getPublicKeyBase64(aliceUid))
        val statusRes = withTimeout(TIMEOUT_MS) {
            socialRepo.createTextStatus(aliceUser, "En direct sur BRAV!", "#570CA2")
        }
        assertTrue("createTextStatus failed: ${statusRes.exceptionOrNull()}", statusRes.isSuccess)

        val locRes = withTimeout(TIMEOUT_MS) {
            socialRepo.publishEncryptedLocation(aliceUser, listOf(aliceUser), 5.348, -4.003, 10f, 15)
        }
        assertTrue("publishEncryptedLocation failed: ${locRes.exceptionOrNull()}", locRes.isSuccess)

        val myShares = withTimeout(TIMEOUT_MS) {
            socialRepo.observeMySharedLocations(aliceUid).first { it.isNotEmpty() }
        }
        val dec = socialRepo.decryptLocation(myShares.first(), aliceUid)
        assertNotNull(dec)
        assertEquals(5.348, dec!!.latitude, 0.0001)

        // Unauthenticated caller must get PERMISSION_DENIED on conversations
        auth.signOut()
        val chatRepo = ChatRepository(firestore, null, crypto)
        try {
            withTimeout(TIMEOUT_MS) {
                chatRepo.observeConversations(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val ffe = checkNotNull(e.unwrapFirestoreException()) { "Expected Firestore exception, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
        }
    }

    @Test
    fun messageEdit30sLimit_andConversationDeletion_workEndToEnd() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("edit_alice_$suffix@test.com")
        val crypto = E2eeCryptoManager(ApplicationProvider.getApplicationContext())
        val userRepo = UserRepository(firestore, crypto)
        userRepo.ensureUserProfileExists(checkNotNull(auth.currentUser), "dev_a")
        val aliceUser = User(userId = aliceUid, displayName = "Alice", publicKey = crypto.getPublicKeyBase64(aliceUid))

        val bobUid = signInTestUser("edit_bob_$suffix@test.com")
        userRepo.ensureUserProfileExists(checkNotNull(auth.currentUser), "dev_b")
        val bobUser = User(userId = bobUid, displayName = "Bob", publicKey = crypto.getPublicKeyBase64(bobUid))

        signInTestUser("edit_alice_$suffix@test.com")
        val chatRepo = ChatRepository(firestore, null, crypto)
        val conv = withTimeout(TIMEOUT_MS) { chatRepo.getOrCreateDirectConversation(aliceUser, bobUser) }.getOrThrow()

        val msgId = withTimeout(TIMEOUT_MS) {
            chatRepo.sendTextMessage(conv, aliceUid, "Message initial", peerPublicKey = bobUser.publicKey)
        }.getOrThrow()

        val recentMsg = withTimeout(TIMEOUT_MS) {
            chatRepo.observeMessages(conv.id).first { it.any { m -> m.id == msgId } }
        }.first { it.id == msgId }

        assertTrue("Message within 30s should be editable", chatRepo.canEditMessage(recentMsg, aliceUid))
        val editOk = withTimeout(TIMEOUT_MS) {
            chatRepo.editMessage(conv, recentMsg, aliceUid, "Message corrigé")
        }
        assertTrue("editMessage within 30s failed: ${editOk.exceptionOrNull()}", editOk.isSuccess)

        val updated = withTimeout(TIMEOUT_MS) {
            chatRepo.observeMessages(conv.id).first { list ->
                val m = list.firstOrNull { it.id == msgId }
                m != null && chatRepo.decryptDisplayText(m, aliceUid, conv) == "Message corrigé"
            }
        }.first { it.id == msgId }
        assertEquals("Message corrigé", chatRepo.decryptDisplayText(updated, aliceUid, conv))

        // Message older than 30s must be rejected by both client helper & Firestore rules
        val expiredTimestamp = com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() - 35_000L))
        val expiredLocalCopy = updated.copy(timestamp = expiredTimestamp)
        assertTrue(!chatRepo.canEditMessage(expiredLocalCopy, aliceUid))
        val expiredClientRes = withTimeout(TIMEOUT_MS) {
            chatRepo.editMessage(conv, expiredLocalCopy, aliceUid, "Trop tard client")
        }
        assertTrue("editMessage after 30s should fail", expiredClientRes.isFailure)

        val oldMsgId = "msg_expired_${UUID.randomUUID().toString().take(8)}"
        withTimeout(TIMEOUT_MS) {
            firestore.collection("conversations").document(conv.id)
                .collection("messages").document(oldMsgId)
                .set(
                    mapOf(
                        "conversationId" to conv.id,
                        "senderId" to aliceUid,
                        "receiverId" to bobUid,
                        "content" to "Vieux message",
                        "messageType" to "text",
                        "status" to "sent",
                        "timestamp" to expiredTimestamp
                    )
                ).await()
        }
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("conversations").document(conv.id)
                    .collection("messages").document(oldMsgId)
                    .update(mapOf("content" to "Piraté après 30s", "isEncrypted" to false, "encryptionIv" to ""))
                    .await()
            }
            fail("Expected PERMISSION_DENIED when editing message older than 30s")
        } catch (e: Exception) {
            val ffe = checkNotNull(e.unwrapFirestoreException())
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
        }

        // Delete entire conversation
        val delConvRes = withTimeout(TIMEOUT_MS) { chatRepo.deleteConversation(conv.id, aliceUid) }
        assertTrue("deleteConversation failed: ${delConvRes.exceptionOrNull()}", delConvRes.isSuccess)
        val remaining = withTimeout(TIMEOUT_MS) {
            firestore.collection("conversations").document(conv.id).get().await()
        }
        assertTrue("Conversation should be deleted", !remaining.exists())
    }

    @Test
    fun freeOffers_createAndDelete_withoutPayment() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("offer_alice_$suffix@test.com")
        val crypto = E2eeCryptoManager(ApplicationProvider.getApplicationContext())
        val socialRepo = SocialRepository(firestore, null, crypto)
        val alice = User(userId = aliceUid, displayName = "Alice Offres")

        val offerId = withTimeout(TIMEOUT_MS) {
            socialRepo.createFreeOffer(
                user = alice,
                title = "Développeur Android disponible",
                category = "Services",
                description = "Publication 100% gratuite sur BRAV Chat",
                locationCity = "Abidjan",
                priceOrSalary = "Gratuit"
            )
        }.getOrThrow()

        val offers = withTimeout(TIMEOUT_MS) {
            socialRepo.observeOffers().first { list -> list.any { it.id == offerId } }
        }
        val created = offers.first { it.id == offerId }
        assertEquals("published", created.status)
        assertEquals("free", created.paymentStatus)

        val delRes = withTimeout(TIMEOUT_MS) { socialRepo.deleteOffer(offerId) }
        assertTrue("deleteOffer failed: ${delRes.exceptionOrNull()}", delRes.isSuccess)
    }

    @Test
    fun callSignalingSignals_participantsExchangeOfferAndAnswer_outsiderDenied() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("sig_alice_$suffix@test.com")
        val bobUid = signInTestUser("sig_bob_$suffix@test.com")
        val eveUid = signInTestUser("sig_eve_$suffix@test.com")

        signInTestUser("sig_alice_$suffix@test.com")
        val callRepo = CallRepository(firestore, null)
        val call = withTimeout(TIMEOUT_MS) {
            callRepo.startCall(aliceUid, "Alice", bobUid, "Bob", "video")
        }.getOrThrow()

        val aliceHistory = withTimeout(TIMEOUT_MS) {
            callRepo.observeCallHistory(aliceUid).first { list -> list.any { it.id == call.id } }
        }
        assertTrue(aliceHistory.any { it.id == call.id })

        val offerRes = withTimeout(TIMEOUT_MS) {
            callRepo.sendCallSignal(call.id, aliceUid, "offer", "{\"type\":\"offer\",\"sdp\":{\"sdp\":\"v=0\"}}")
        }
        assertTrue("sendCallSignal failed: ${offerRes.exceptionOrNull()}", offerRes.isSuccess)

        signInTestUser("sig_bob_$suffix@test.com")
        val bobIncoming = withTimeout(TIMEOUT_MS) {
            callRepo.observeIncomingCalls(bobUid).first { list -> list.any { it.id == call.id } }
        }
        assertTrue("Bob should receive incoming call in real time", bobIncoming.any { it.id == call.id })
        val bobReceived = withTimeout(TIMEOUT_MS) {
            callRepo.observeCallSignals(call.id, bobUid).first { it.isNotEmpty() }
        }
        assertEquals("offer", bobReceived.first().type)

        signInTestUser("sig_eve_$suffix@test.com")
        try {
            withTimeout(TIMEOUT_MS) {
                callRepo.observeCallSignals(call.id, eveUid).first()
            }
            fail("Expected PERMISSION_DENIED for Eve observing call signals")
        } catch (e: Exception) {
            val ffe = checkNotNull(e.unwrapFirestoreException())
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
        }
    }

    @Test
    fun callHistoryDeletion_hidesCallsPerUser_preservingOtherParticipant() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("hist_alice_$suffix@test.com")
        val bobUid = signInTestUser("hist_bob_$suffix@test.com")

        signInTestUser("hist_alice_$suffix@test.com")
        val callRepo = CallRepository(firestore, null)
        val call1 = withTimeout(TIMEOUT_MS) { callRepo.startCall(aliceUid, "Alice", bobUid, "Bob", "audio") }.getOrThrow()
        val call2 = withTimeout(TIMEOUT_MS) { callRepo.startCall(aliceUid, "Alice", bobUid, "Bob", "video") }.getOrThrow()
        withTimeout(TIMEOUT_MS) { callRepo.updateCallStatus(call1.id, "ended", missed = false, durationSeconds = 18L) }
        withTimeout(TIMEOUT_MS) { callRepo.updateCallStatus(call2.id, "ended", missed = false, durationSeconds = 45L) }

        val delOne = withTimeout(TIMEOUT_MS) { callRepo.deleteCallFromHistory(call1.id, aliceUid) }
        assertTrue("deleteCallFromHistory failed: ${delOne.exceptionOrNull()}", delOne.isSuccess)
        val afterOne = withTimeout(TIMEOUT_MS) {
            callRepo.observeCallHistory(aliceUid).first { list -> list.none { it.id == call1.id } }
        }
        assertTrue(afterOne.any { it.id == call2.id })

        val clearAll = withTimeout(TIMEOUT_MS) { callRepo.clearCallHistory(aliceUid, listOf(call2.id)) }
        assertTrue("clearCallHistory failed: ${clearAll.exceptionOrNull()}", clearAll.isSuccess)
        val afterClear = withTimeout(TIMEOUT_MS) {
            callRepo.observeCallHistory(aliceUid).first { it.isEmpty() }
        }
        assertTrue(afterClear.isEmpty())

        // Bob must still see both calls in his own history
        signInTestUser("hist_bob_$suffix@test.com")
        val bobHistory = withTimeout(TIMEOUT_MS) {
            callRepo.observeCallHistory(bobUid).first { it.size >= 2 }
        }
        assertTrue(bobHistory.any { it.id == call1.id } && bobHistory.any { it.id == call2.id })
    }

    @Test
    fun statusLikesReactionsAndComments_workEndToEnd() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("st_alice_$suffix@test.com")
        val bobUid = signInTestUser("st_bob_$suffix@test.com")

        signInTestUser("st_alice_$suffix@test.com")
        val crypto = E2eeCryptoManager(ApplicationProvider.getApplicationContext())
        val socialRepo = SocialRepository(firestore, null, crypto)
        val alice = User(userId = aliceUid, displayName = "Alice")
        val statusId = withTimeout(TIMEOUT_MS) {
            socialRepo.createTextStatus(alice, "Nouveau statut pro!", "#570CA2")
        }.getOrThrow()

        signInTestUser("st_bob_$suffix@test.com")
        val bob = User(userId = bobUid, displayName = "Bob")
        val st = withTimeout(TIMEOUT_MS) {
            socialRepo.observeStatuses(bobUid).first { list -> list.any { it.id == statusId } }
        }.first { it.id == statusId }

        assertTrue(withTimeout(TIMEOUT_MS) { socialRepo.toggleStatusLike(st, bobUid) }.isSuccess)
        assertTrue(withTimeout(TIMEOUT_MS) { socialRepo.toggleStatusReaction(st, bobUid, "cyber_bot") }.isSuccess)
        val cmtRes = withTimeout(TIMEOUT_MS) {
            socialRepo.addStatusComment(statusId, bob, "Super statut futuriste !", "rocket_turbo")
        }
        assertTrue("addStatusComment failed: ${cmtRes.exceptionOrNull()}", cmtRes.isSuccess)

        val comments = withTimeout(TIMEOUT_MS) {
            socialRepo.observeStatusComments(statusId).first { it.isNotEmpty() }
        }
        assertEquals("Super statut futuriste !", comments.first().text)
        assertEquals("rocket_turbo", comments.first().reactionKey)

        val delCmt = withTimeout(TIMEOUT_MS) {
            socialRepo.deleteStatusComment(statusId, comments.first().id)
        }
        assertTrue("deleteStatusComment failed: ${delCmt.exceptionOrNull()}", delCmt.isSuccess)
    }

    @Test
    fun profileValidationAndRemovePhoto_preserveUserFieldsAndClearAvatar() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("prof_alice_$suffix@test.com")
        val crypto = E2eeCryptoManager(ApplicationProvider.getApplicationContext())
        val userRepo = UserRepository(firestore, crypto)
        userRepo.ensureUserProfileExists(checkNotNull(auth.currentUser), "dev_prof")

        assertTrue(!userRepo.validateProfileInput("A", "alice", "Bio", "+2250700000001", "a@b.com").isValid)
        assertTrue(!userRepo.validateProfileInput("Alice", "Alice", "Bio", "123", "not_email").isValid)
        assertTrue(userRepo.validateProfileInput("Alice Brav", "alice_brav", "Disponible", "+2250700000001", "alice@brav.com").isValid)

        val saveWithPhoto = withTimeout(TIMEOUT_MS) {
            userRepo.updateProfile(
                uid = aliceUid,
                displayName = "Alice Brav",
                username = "alice_p_$suffix",
                bio = "Profil pro",
                locationCity = "Abidjan",
                locationDetails = "Plateau",
                avatarUrl = "https://cdn.example.com/alice.jpg"
            )
        }
        assertTrue(saveWithPhoto.isSuccess)
        assertEquals("https://cdn.example.com/alice.jpg", withTimeout(TIMEOUT_MS) { userRepo.getUserById(aliceUid) }?.avatarUrl)

        val removeRes = withTimeout(TIMEOUT_MS) { userRepo.removeProfilePhoto(aliceUid) }
        assertTrue("removeProfilePhoto failed: ${removeRes.exceptionOrNull()}", removeRes.isSuccess)

        val afterRemove = withTimeout(TIMEOUT_MS) { userRepo.getUserById(aliceUid) }
        assertNotNull(afterRemove)
        assertEquals("", afterRemove?.avatarUrl)
        assertEquals("Alice Brav", afterRemove?.displayName)
    }

    @Test
    fun callAvatarsAndHoldState_syncBetweenCallerAndReceiver() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("hold_alice_$suffix@test.com")
        val bobUid = signInTestUser("hold_bob_$suffix@test.com")

        signInTestUser("hold_alice_$suffix@test.com")
        val callRepo = CallRepository(firestore, null)
        val call = withTimeout(TIMEOUT_MS) {
            callRepo.startCall(
                callerId = aliceUid,
                callerName = "Alice",
                receiverId = bobUid,
                receiverName = "Bob",
                callType = "audio",
                callerAvatar = "https://cdn.example.com/alice.jpg",
                callerUsername = "alice_call",
                receiverAvatar = "https://cdn.example.com/bob.jpg",
                receiverUsername = "bob_call"
            )
        }.getOrThrow()

        assertEquals("https://cdn.example.com/alice.jpg", call.callerAvatar)
        assertEquals("https://cdn.example.com/bob.jpg", call.receiverAvatar)

        assertTrue(withTimeout(TIMEOUT_MS) { callRepo.updateCallStatus(call.id, "accepted") }.isSuccess)
        assertTrue(withTimeout(TIMEOUT_MS) { callRepo.setCallHold(call.id, true, aliceUid) }.isSuccess)

        val heldDoc = withTimeout(TIMEOUT_MS) {
            callRepo.observeCall(call.id).first { it?.status == "on_hold" }
        }
        assertEquals(aliceUid, heldDoc?.heldByUid)

        assertTrue(withTimeout(TIMEOUT_MS) { callRepo.setCallHold(call.id, false, aliceUid) }.isSuccess)
        val resumedDoc = withTimeout(TIMEOUT_MS) {
            callRepo.observeCall(call.id).first { it?.status == "accepted" }
        }
        assertEquals("", resumedDoc?.heldByUid)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
    }
}
