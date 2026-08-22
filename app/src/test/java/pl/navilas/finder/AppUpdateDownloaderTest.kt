package pl.navilas.finder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.navilas.finder.update.AppUpdateDownloader
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory

class AppUpdateDownloaderTest {
    @Test
    fun verifySha256_matchesKnownEmptyFileHash() {
        val dir = createTempDirectory("navilas-update-test").toFile()
        val file = File(dir, "empty.apk")
        file.writeBytes(byteArrayOf())
        val downloader = AppUpdateDownloader()
        assertTrue(
            downloader.verifySha256(
                file,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ),
        )
        dir.deleteRecursively()
    }

    @Test
    fun verifySha256_rejectsWrongHash() {
        val dir = createTempDirectory("navilas-update-test").toFile()
        val file = File(dir, "payload.apk")
        file.writeText("navilas", StandardCharsets.UTF_8)
        val downloader = AppUpdateDownloader()
        assertFalse(
            downloader.verifySha256(
                file,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ),
        )
        dir.deleteRecursively()
    }
}
