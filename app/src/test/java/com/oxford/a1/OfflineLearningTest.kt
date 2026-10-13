package com.oxford.a1

import android.app.Application
import android.net.Uri
import com.oxford.a1.ai.LearningPrompt
import com.oxford.a1.ai.ModelStore
import com.oxford.a1.data.model.Word
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OfflineLearningTest {
    @Before fun configureDisk() {
        ShadowStatFs.registerStats(RuntimeEnvironment.getApplication().noBackupFilesDir.path,
            1_000_000, 1_000_000, 1_000_000)
    }
    private fun fixture(): ByteArray = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        .put(byteArrayOf(71, 71, 85, 70)).putInt(3).putLong(1).putLong(1).array()

    @Test fun targetsContainOnlyLearnedWordsAndReflectUnmarking() {
        val known = Word(1, "apple", "", true, emptyList())
        val unknown = Word(2, "zebra", "", false, emptyList())
        val targets = LearningPrompt.targets(listOf(known, unknown))
        assertEquals(listOf("apple"), targets)
        assertTrue(LearningPrompt.build(targets).contains("apple"))
        assertFalse(LearningPrompt.build(targets).contains("zebra"))
        assertTrue(LearningPrompt.targets(listOf(known.copy(learned = false))).isEmpty())
    }
    @Test fun noPromptWithoutLearnedWords() {
        assertThrows(IllegalArgumentException::class.java) { LearningPrompt.build(emptyList()) }
    }
    @Test fun streamCopyPreservesBytesAndReportsProgress() = runBlocking {
        val file = File.createTempFile("model", ".gguf")
        try {
            var bytes = 0L
            ModelStore.copyValidated(ByteArrayInputStream(fixture()), file, { Long.MAX_VALUE }) { bytes = it }
            assertArrayEquals(fixture(), file.readBytes())
            assertEquals(2048L, bytes)
        } finally { file.delete() }
    }
    @Test fun rejectsInvalidHeaderAndLowDiskSpace() = runBlocking {
        val file = File.createTempFile("model", ".gguf")
        try {
            for (data in listOf(ByteArray(24), byteArrayOf(71, 71, 85, 70), fixture())) {
                val result = runCatching { ModelStore.copyValidated(ByteArrayInputStream(data), file, { 0L }) {} }
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            }
        } finally { file.delete() }
    }
    @Test fun failedImportPreservesExistingModelAndCleansTemporaryFile() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = ModelStore(context)
        store.model.writeText("existing-model")
        val invalid = File(context.cacheDir, "invalid.gguf").apply { writeBytes(ByteArray(100)) }
        assertTrue(runCatching { store.import(Uri.fromFile(invalid)) {} }.isFailure)
        assertEquals("existing-model", store.model.readText())
        assertFalse(File(store.model.parentFile, "import.part").exists())
    }
    @Test fun successfulImportReplacesModelOnlyAfterCopyCompletes() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = ModelStore(context)
        store.model.writeText("existing-model")
        val source = File(context.cacheDir, "replacement.gguf").apply { writeBytes(fixture()) }
        store.import(Uri.fromFile(source)) { assertEquals("existing-model", store.model.readText()) }
        assertArrayEquals(fixture(), store.model.readBytes())
        assertFalse(File(store.model.parentFile, "import.part").exists())
    }
    @Test fun cancelledImportPreservesExistingModelAndCleansTemporaryFile() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = ModelStore(context)
        store.model.writeText("existing-model")
        val source = File(context.cacheDir, "source.gguf").apply { writeBytes(fixture()) }
        val operation = launch { store.import(Uri.fromFile(source)) { cancel() } }
        operation.join()
        assertTrue(operation.isCancelled)
        assertEquals("existing-model", store.model.readText())
        assertFalse(File(store.model.parentFile, "import.part").exists())
    }
}
