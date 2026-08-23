package pl.navilas.finder.data.saved

import org.json.JSONArray
import org.json.JSONObject
import pl.navilas.finder.domain.SavedPoint
import pl.navilas.finder.domain.SavedPointCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SavedPointsBackupSnapshot(
    val categories: List<SavedPointCategory>,
    val points: List<SavedPoint>,
)

sealed class SavedPointsBackupParseResult {
    data class Success(
        val snapshot: SavedPointsBackupSnapshot,
        val skippedPoints: Int,
    ) : SavedPointsBackupParseResult()

    data class Failure(val message: String) : SavedPointsBackupParseResult()
}

data class SavedPointsImportResult(
    val addedPoints: Int,
    val updatedPoints: Int,
    val skippedPoints: Int,
    val addedCategories: Int,
)

object SavedPointsBackupCodec {
    const val FORMAT_VERSION = 1
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    const val MIME_TYPE = "application/json"

    fun suggestedExportFilename(): String {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return "navilas-zapisane-$date.json"
    }

    fun encodeExport(store: SavedPointsStore, appVersion: String, exportedAtMs: Long): String {
        val points = store.allPoints()
        val envelope = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAtMs", exportedAtMs)
            .put("appVersion", appVersion)
            .put("pointsCount", points.size)
        store.writeSnapshotTo(envelope)
        return envelope.toString(2)
    }

    fun parseImport(
        text: String,
        parseSnapshot: (JSONObject) -> SavedPointsBackupParseResult,
    ): SavedPointsBackupParseResult {
        if (text.length > MAX_FILE_BYTES) {
            return SavedPointsBackupParseResult.Failure("Plik jest zbyt duży.")
        }
        val root = runCatching { JSONObject(text) }.getOrElse {
            return SavedPointsBackupParseResult.Failure("Nieprawidłowy format pliku (oczekiwano JSON).")
        }
        val formatVersion = root.optInt("formatVersion", 0)
        if (formatVersion > FORMAT_VERSION) {
            return SavedPointsBackupParseResult.Failure(
                "Nieobsługiwana wersja kopii (formatVersion=$formatVersion).",
            )
        }
        return parseSnapshot(root)
    }
}
