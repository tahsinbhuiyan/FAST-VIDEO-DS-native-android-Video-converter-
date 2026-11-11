package com.example.fastvideo.data

import android.content.Context
import com.example.fastvideo.format.FvFormat
import com.example.fastvideo.model.ConversionJob
import com.example.fastvideo.model.ConversionPreset
import com.example.fastvideo.model.ConversionSettings
import com.example.fastvideo.model.FitMode
import com.example.fastvideo.model.JobStatus
import com.example.fastvideo.model.SourceVideoMeta
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConversionHistoryManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("fv_converter_prefs", Context.MODE_PRIVATE)
    private val historyFile = File(context.filesDir, "conversion_history.json")

    @Synchronized
    fun getHistory(): List<ConversionJob> {
        if (!historyFile.exists()) return emptyList()
        val list = mutableListOf<ConversionJob>()
        try {
            val jsonStr = historyFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val job = parseJobFromJson(obj)
                list.add(job)
            }
        } catch (e: Exception) {
            // ignored
        }
        return list.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun saveJob(job: ConversionJob) {
        val current = getHistory().toMutableList()
        val idx = current.indexOfFirst { it.id == job.id }
        if (idx >= 0) {
            current[idx] = job
        } else {
            current.add(0, job)
        }
        writeHistory(current)
    }

    @Synchronized
    fun deleteJob(jobId: String): Boolean {
        val current = getHistory().toMutableList()
        val job = current.firstOrNull { it.id == jobId }
        if (job != null) {
            if (job.outputFilePath.isNotEmpty()) {
                val f = File(job.outputFilePath)
                if (f.exists()) f.delete()
            }
            current.removeAll { it.id == jobId }
            writeHistory(current)
            return true
        }
        return false
    }

    @Synchronized
    fun clearHistory() {
        historyFile.delete()
    }

    private fun writeHistory(list: List<ConversionJob>) {
        try {
            val array = JSONArray()
            for (job in list) {
                array.put(serializeJobToJson(job))
            }
            historyFile.writeText(array.toString())
        } catch (e: Exception) {
            // ignored
        }
    }

    fun getSavedSettings(): ConversionSettings {
        val presetName = prefs.getString("preset", ConversionPreset.DSI_WIDESCREEN.name) ?: ConversionPreset.DSI_WIDESCREEN.name
        val preset = try { ConversionPreset.valueOf(presetName) } catch (e: Exception) { ConversionPreset.DSI_WIDESCREEN }
        return ConversionSettings(
            preset = preset,
            width = prefs.getInt("width", 256),
            height = prefs.getInt("height", preset.height),
            fps = prefs.getInt("fps", 20),
            q = prefs.getInt("q", 30),
            audioRate = prefs.getInt("audioRate", 32000),
            stereo = prefs.getBoolean("stereo", true),
            maxGopLength = prefs.getInt("maxGopLength", 120),
            fitMode = FitMode.LETTERBOX
        )
    }

    fun saveSettings(settings: ConversionSettings) {
        prefs.edit().apply {
            putString("preset", settings.preset.name)
            putInt("width", settings.width)
            putInt("height", settings.height)
            putInt("fps", settings.fps)
            putInt("q", settings.q)
            putInt("audioRate", settings.audioRate)
            putBoolean("stereo", settings.stereo)
            putInt("maxGopLength", settings.maxGopLength)
            apply()
        }
    }

    private fun serializeJobToJson(job: ConversionJob): JSONObject {
        val obj = JSONObject()
        obj.put("id", job.id)
        obj.put("status", job.status.name)
        obj.put("progress", job.progress.toDouble())
        obj.put("currentFrame", job.currentFrame)
        obj.put("totalFrames", job.totalFrames)
        obj.put("elapsedMs", job.elapsedMs)
        obj.put("outputFilePath", job.outputFilePath)
        obj.put("outputSizeBytes", job.outputSizeBytes)
        obj.put("errorMessage", job.errorMessage ?: "")
        obj.put("createdAt", job.createdAt)

        val src = JSONObject()
        src.put("uri", job.sourceMeta.uri)
        src.put("name", job.sourceMeta.name)
        src.put("sizeBytes", job.sourceMeta.sizeBytes)
        src.put("durationMs", job.sourceMeta.durationMs)
        src.put("width", job.sourceMeta.width)
        src.put("height", job.sourceMeta.height)
        src.put("fps", job.sourceMeta.fps)
        src.put("hasAudio", job.sourceMeta.hasAudio)
        src.put("audioSampleRate", job.sourceMeta.audioSampleRate)
        src.put("audioChannels", job.sourceMeta.audioChannels)
        obj.put("sourceMeta", src)

        val set = JSONObject()
        set.put("preset", job.settings.preset.name)
        set.put("width", job.settings.width)
        set.put("height", job.settings.height)
        set.put("fps", job.settings.fps)
        set.put("q", job.settings.q)
        set.put("audioRate", job.settings.audioRate)
        set.put("stereo", job.settings.stereo)
        set.put("maxGopLength", job.settings.maxGopLength)
        obj.put("settings", set)

        return obj
    }

    private fun parseJobFromJson(obj: JSONObject): ConversionJob {
        val id = obj.getString("id")
        val status = try { JobStatus.valueOf(obj.getString("status")) } catch (e: Exception) { JobStatus.COMPLETED }
        val progress = obj.optDouble("progress", 1.0).toFloat()
        val currentFrame = obj.optInt("currentFrame", 0)
        val totalFrames = obj.optInt("totalFrames", 0)
        val elapsedMs = obj.optLong("elapsedMs", 0L)
        val outputFilePath = obj.optString("outputFilePath", "")
        val outputSizeBytes = obj.optLong("outputSizeBytes", 0L)
        val errorMessage = obj.optString("errorMessage").takeIf { it.isNotEmpty() }
        val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

        val srcObj = obj.getJSONObject("sourceMeta")
        val sourceMeta = SourceVideoMeta(
            uri = srcObj.getString("uri"),
            name = srcObj.getString("name"),
            sizeBytes = srcObj.optLong("sizeBytes", 0L),
            durationMs = srcObj.optLong("durationMs", 0L),
            width = srcObj.optInt("width", 256),
            height = srcObj.optInt("height", 144),
            fps = srcObj.optDouble("fps", 30.0),
            hasAudio = srcObj.optBoolean("hasAudio", true),
            audioSampleRate = srcObj.optInt("audioSampleRate", 44100),
            audioChannels = srcObj.optInt("audioChannels", 2)
        )

        val setObj = obj.getJSONObject("settings")
        val preset = try { ConversionPreset.valueOf(setObj.getString("preset")) } catch (e: Exception) { ConversionPreset.DSI_WIDESCREEN }
        val settings = ConversionSettings(
            preset = preset,
            width = setObj.optInt("width", 256),
            height = setObj.optInt("height", 144),
            fps = setObj.optInt("fps", 20),
            q = setObj.optInt("q", 30),
            audioRate = setObj.optInt("audioRate", 47605),
            stereo = setObj.optBoolean("stereo", true),
            maxGopLength = setObj.optInt("maxGopLength", 120),
            fitMode = FitMode.LETTERBOX
        )

        val validationReport = if (outputFilePath.isNotEmpty() && File(outputFilePath).exists()) {
            FvFormat.validateFile(File(outputFilePath))
        } else null

        return ConversionJob(
            id = id,
            sourceMeta = sourceMeta,
            settings = settings,
            status = status,
            progress = progress,
            currentFrame = currentFrame,
            totalFrames = totalFrames,
            elapsedMs = elapsedMs,
            outputFilePath = outputFilePath,
            outputSizeBytes = outputSizeBytes,
            validationReport = validationReport,
            errorMessage = errorMessage,
            createdAt = createdAt
        )
    }
}
