package com.example.smartwificonnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartwificonnect.data.local.SavedWifiRecordDraft
import com.example.smartwificonnect.data.local.WifiHistoryDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests cho WifiHistoryDbHelper — chạy trên device/emulator.
 * Mỗi test dùng DB riêng biệt, được xóa sạch trước và sau test.
 */
@RunWith(AndroidJUnit4::class)
class WifiHistoryDbHelperTest {

    private lateinit var dbHelper: WifiHistoryDbHelper

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun draftOf(
        ssid: String,
        password: String = "pass123",
        baseUrl: String = "http://localhost:8080/",
        ocrText: String = "WiFi Name: $ssid\nPassword: $password",
        sourceFormat: String = "text_label",
        confidence: Double? = 0.95,
        aiConfidence: Double? = 0.88,
        aiSuggestion: String = "Ok",
        aiRecommendation: String = "review",
        aiShouldAutoConnect: Boolean = false,
        aiFlags: List<String> = emptyList(),
        fuzzyBestMatch: String? = null,
        fuzzyScore: Double? = null,
    ) = SavedWifiRecordDraft(
        baseUrl = baseUrl,
        ocrText = ocrText,
        ssid = ssid,
        password = password,
        sourceFormat = sourceFormat,
        confidence = confidence,
        aiConfidence = aiConfidence,
        aiSuggestion = aiSuggestion,
        aiRecommendation = aiRecommendation,
        aiShouldAutoConnect = aiShouldAutoConnect,
        aiFlags = aiFlags,
        fuzzyBestMatch = fuzzyBestMatch,
        fuzzyScore = fuzzyScore,
    )

    @Before
    fun setUp() {
        context.deleteDatabase("smart_wifi_connect.db")
        dbHelper = WifiHistoryDbHelper(context)
    }

    @After
    fun tearDown() {
        dbHelper.close()
        context.deleteDatabase("smart_wifi_connect.db")
    }

    // ─── insert ───────────────────────────────────────────────────────────────

    @Test
    fun insert_basicRecord_returnsRecordWithPositiveId() {
        val record = dbHelper.insert(draftOf("HomeNet"))
        assertTrue(record.id > 0)
        assertEquals("HomeNet", record.ssid)
        assertEquals("pass123", record.password)
    }

    @Test
    fun insert_allFields_persistsCorrectly() {
        val draft = draftOf(
            ssid = "FullFieldNet",
            password = "fullP@ss",
            baseUrl = "http://server:9090/",
            ocrText = "raw ocr text",
            sourceFormat = "wifi_qr_like",
            confidence = 0.99,
            aiConfidence = 0.77,
            aiSuggestion = "Data looks good",
            aiRecommendation = "auto_connect",
            aiShouldAutoConnect = true,
            aiFlags = listOf("ocr_ambiguous_characters", "low_confidence"),
            fuzzyBestMatch = "FullFieldNet_5G",
            fuzzyScore = 0.92,
        )
        val record = dbHelper.insert(draft)

        assertEquals("FullFieldNet", record.ssid)
        assertEquals("fullP@ss", record.password)
        assertEquals("http://server:9090/", record.baseUrl)
        assertEquals("raw ocr text", record.ocrText)
        assertEquals("wifi_qr_like", record.sourceFormat)
        assertEquals(0.99, record.confidence ?: 0.0, 0.0001)
        assertEquals(0.77, record.aiConfidence ?: 0.0, 0.0001)
        assertEquals("Data looks good", record.aiSuggestion)
        assertEquals("auto_connect", record.aiRecommendation)
        assertTrue(record.aiShouldAutoConnect)
        assertEquals(listOf("ocr_ambiguous_characters", "low_confidence"), record.aiFlags)
        assertEquals("FullFieldNet_5G", record.fuzzyBestMatch)
        assertEquals(0.92, record.fuzzyScore ?: 0.0, 0.0001)
        assertTrue(record.createdAtMillis > 0)
    }

    @Test
    fun insert_nullableNullFields_persistsNulls() {
        val draft = draftOf(
            ssid = "NullFieldNet",
            confidence = null,
            aiConfidence = null,
            fuzzyBestMatch = null,
            fuzzyScore = null,
        )
        val record = dbHelper.insert(draft)
        assertNull(record.confidence)
        assertNull(record.aiConfidence)
        assertNull(record.fuzzyBestMatch)
        assertNull(record.fuzzyScore)
    }

    @Test
    fun insert_emptyAiFlags_returnsEmptyList() {
        val record = dbHelper.insert(draftOf("FlagEmptyNet", aiFlags = emptyList()))
        assertEquals(emptyList<String>(), record.aiFlags)
    }

    @Test
    fun insert_multipleFlags_persistsAllFlags() {
        val flags = listOf("flag_a", "flag_b", "flag_c")
        val record = dbHelper.insert(draftOf("FlagNet", aiFlags = flags))
        assertEquals(flags, record.aiFlags)
    }

    @Test
    fun insert_aiShouldAutoConnectTrue_persistsTrue() {
        val record = dbHelper.insert(draftOf("AutoNet", aiShouldAutoConnect = true))
        assertTrue(record.aiShouldAutoConnect)
    }

    @Test
    fun insert_aiShouldAutoConnectFalse_persistsFalse() {
        val record = dbHelper.insert(draftOf("ManualNet", aiShouldAutoConnect = false))
        assertFalse(record.aiShouldAutoConnect)
    }

    // ─── getLatest ────────────────────────────────────────────────────────────

    @Test
    fun getLatest_emptyDb_returnsNull() {
        assertNull(dbHelper.getLatest())
    }

    @Test
    fun getLatest_singleRecord_returnsThatRecord() {
        dbHelper.insert(draftOf("OnlyNet"))
        val latest = dbHelper.getLatest()
        assertNotNull(latest)
        assertEquals("OnlyNet", latest!!.ssid)
    }

    @Test
    fun getLatest_multipleRecords_returnsMostRecent() {
        dbHelper.insert(draftOf("OldNet"))
        Thread.sleep(10) // đảm bảo createdAtMillis khác nhau
        dbHelper.insert(draftOf("NewNet"))
        val latest = dbHelper.getLatest()
        assertEquals("NewNet", latest!!.ssid)
    }

    // ─── getAll ───────────────────────────────────────────────────────────────

    @Test
    fun getAll_emptyDb_returnsEmptyList() {
        assertTrue(dbHelper.getAll().isEmpty())
    }

    @Test
    fun getAll_threeRecords_returnsAllOrderedByNewest() {
        dbHelper.insert(draftOf("Net1"))
        Thread.sleep(10)
        dbHelper.insert(draftOf("Net2"))
        Thread.sleep(10)
        dbHelper.insert(draftOf("Net3"))

        val all = dbHelper.getAll()
        assertEquals(3, all.size)
        assertEquals("Net3", all[0].ssid)
        assertEquals("Net2", all[1].ssid)
        assertEquals("Net1", all[2].ssid)
    }

    @Test
    fun getAll_withLimit_respectsLimit() {
        repeat(10) { dbHelper.insert(draftOf("Net$it")) }
        val limited = dbHelper.getAll(limit = 3)
        assertEquals(3, limited.size)
    }

    @Test
    fun getAll_limitLargerThanCount_returnsAll() {
        dbHelper.insert(draftOf("A"))
        dbHelper.insert(draftOf("B"))
        val all = dbHelper.getAll(limit = 100)
        assertEquals(2, all.size)
    }

    // ─── deleteById ───────────────────────────────────────────────────────────

    @Test
    fun deleteById_existingId_returnsTrueAndRecordIsGone() {
        val record = dbHelper.insert(draftOf("ToDelete"))
        val deleted = dbHelper.deleteById(record.id)
        assertTrue(deleted)
        assertTrue(dbHelper.getAll().none { it.id == record.id })
    }

    @Test
    fun deleteById_nonExistentId_returnsFalse() {
        val deleted = dbHelper.deleteById(99999L)
        assertFalse(deleted)
    }

    @Test
    fun deleteById_onlyDeletesTargetRecord_othersRemain() {
        val r1 = dbHelper.insert(draftOf("Keep1"))
        val r2 = dbHelper.insert(draftOf("Delete"))
        val r3 = dbHelper.insert(draftOf("Keep2"))

        dbHelper.deleteById(r2.id)

        val remaining = dbHelper.getAll()
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.id == r1.id })
        assertTrue(remaining.any { it.id == r3.id })
        assertFalse(remaining.any { it.id == r2.id })
    }

    // ─── Round-trip data integrity ────────────────────────────────────────────

    @Test
    fun roundTrip_insertThenGetAll_fieldsMatchExactly() {
        val draft = draftOf(
            ssid = "RoundTripNet",
            password = "rt_pass",
            baseUrl = "http://rt:8080/",
            ocrText = "rt ocr",
            sourceFormat = "connected_manual",
            confidence = 0.85,
            aiConfidence = 0.70,
            aiSuggestion = "rt suggestion",
            aiRecommendation = "review",
            aiShouldAutoConnect = false,
            aiFlags = listOf("rt_flag"),
            fuzzyBestMatch = "RoundTripNet_5G",
            fuzzyScore = 0.85,
        )
        val inserted = dbHelper.insert(draft)
        val fetched = dbHelper.getAll().first { it.id == inserted.id }

        assertEquals(inserted.ssid, fetched.ssid)
        assertEquals(inserted.password, fetched.password)
        assertEquals(inserted.baseUrl, fetched.baseUrl)
        assertEquals(inserted.ocrText, fetched.ocrText)
        assertEquals(inserted.sourceFormat, fetched.sourceFormat)
        assertEquals(inserted.confidence, fetched.confidence)
        assertEquals(inserted.aiConfidence, fetched.aiConfidence)
        assertEquals(inserted.aiSuggestion, fetched.aiSuggestion)
        assertEquals(inserted.aiRecommendation, fetched.aiRecommendation)
        assertEquals(inserted.aiShouldAutoConnect, fetched.aiShouldAutoConnect)
        assertEquals(inserted.aiFlags, fetched.aiFlags)
        assertEquals(inserted.fuzzyBestMatch, fetched.fuzzyBestMatch)
        assertEquals(inserted.fuzzyScore, fetched.fuzzyScore)
        assertEquals(inserted.createdAtMillis, fetched.createdAtMillis)
    }

    @Test
    fun roundTrip_nullConfidenceFields_surviveGetAll() {
        val draft = draftOf(
            ssid = "NullRt",
            confidence = null,
            aiConfidence = null,
            fuzzyBestMatch = null,
            fuzzyScore = null,
        )
        val inserted = dbHelper.insert(draft)
        val fetched = dbHelper.getAll().first { it.id == inserted.id }
        assertNull(fetched.confidence)
        assertNull(fetched.aiConfidence)
        assertNull(fetched.fuzzyBestMatch)
        assertNull(fetched.fuzzyScore)
    }

    // ─── Concurrency / stress ─────────────────────────────────────────────────

    @Test
    fun insert_fiftyRecords_allStoredAndRetrievable() {
        repeat(50) { i -> dbHelper.insert(draftOf("Net$i")) }
        val all = dbHelper.getAll(limit = 50)
        assertEquals(50, all.size)
    }
}
