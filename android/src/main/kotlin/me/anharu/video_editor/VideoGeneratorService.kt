package me.anharu.video_editor

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.daasuu.mp4compose.composer.Mp4Composer
import com.daasuu.mp4compose.filter.*
import me.anharu.video_editor.filter.GlImageOverlayFilter
import me.anharu.video_editor.ImageOverlay
import io.flutter.plugin.common.MethodChannel.Result
import android.graphics.Paint.Align
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.util.EventLog
import com.daasuu.mp4compose.FillMode
import com.daasuu.mp4compose.Rotation
import com.daasuu.mp4compose.VideoFormatMimeType
import io.flutter.plugin.common.EventChannel
import me.anharu.video_editor.filter.GlColorBlendFilter
import me.anharu.video_editor.filter.GlTextOverlayFilter
import java.util.logging.StreamHandler


interface VideoGeneratorServiceInterface {
    fun writeVideofile(processing: HashMap<String,HashMap<String,Any>>, result: Result, activity: Activity, eventSink: EventChannel.EventSink?);
}

class VideoGeneratorService(
        private val composer: Mp4Composer
) : VideoGeneratorServiceInterface {
    override fun writeVideofile(
        processing: HashMap<String, HashMap<String, Any>>,
        result: Result,
        activity: Activity,
        eventSink: EventChannel.EventSink?
    ) {
        val filters: MutableList<GlFilter> = mutableListOf()
        try {
            processing.forEach { (k, v) ->
                when (k) {
                    "Filter" -> {
                        val passFilter = Filter(v)
                        val filter = GlColorBlendFilter(passFilter)
                        filters.add(filter)
                    }
                    "TextOverlay" -> {
                        val textOverlay = TextOverlay(v)
                        filters.add(GlTextOverlayFilter(textOverlay))
                    }
                    "ImageOverlay" -> {
                        val imageOverlay = ImageOverlay(v)
                        filters.add(GlImageOverlayFilter(imageOverlay))
                    }
                }
            }
        } catch (e: Exception) {
            println("Exception during processing: ${e}")
            activity.runOnUiThread {
                result.error("FILTER_PROCESSING_ERROR", "Error processing filters: ${e.message}", null)
            }
            return
        }

        try {
            composer.filter(GlFilterGroup(filters))
                .fillMode(FillMode.PRESERVE_ASPECT_FIT)
                .videoFormatMimeType(VideoFormatMimeType.MPEG4)
                .listener(object : Mp4Composer.Listener {
                    override fun onProgress(progress: Double) {
                        println("Progress ${progress}")
                        activity.runOnUiThread {
                            eventSink?.success(progress) ?: println("Event Channel is null.")
                        }
                    }

                    override fun onCompleted() {
                        println("onCompleted")
                        activity.runOnUiThread {
                            result.success(null)
                        }
                    }

                    override fun onCanceled() {
                        println("onCanceled")
                        activity.runOnUiThread {
                            result.error("VIDEO_PROCESSING_CANCELED", "Video processing was canceled", null)
                        }
                    }

                    override fun onFailed(exception: Exception) {
                        println("onFailed: ${exception}")
                        activity.runOnUiThread {
                            result.error("VIDEO_PROCESSING_FAILED",
                                "Video processing failed: ${exception.message}",
                                exception.stackTraceToString())
                        }
                    }
                }).start()
        } catch (e: IllegalArgumentException) {
            // Specifically handle MediaCodec configuration errors
            println("MediaCodec configuration error: ${e}")
            activity.runOnUiThread {
                result.error("MEDIA_CODEC_ERROR",
                    "Video encoder configuration error: ${e.message}",
                    "Check video format and encoder parameters")
            }
        } catch (e: Exception) {
            println("Unexpected error: ${e}")
            activity.runOnUiThread {
                result.error("UNEXPECTED_ERROR",
                    "Unexpected error during video processing: ${e.message}",
                    e.stackTraceToString())
            }
        }
    }
}

