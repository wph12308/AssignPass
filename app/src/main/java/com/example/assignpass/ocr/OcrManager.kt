package com.example.assignpass.ocr

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ML Kit 中文文字识别管理器
 *
 * 优化：返回带 boundingBox 坐标的结构化数据 [OcrTextLine] 列表，
 * 而非纯文本字符串，使下游解析器能利用空间信息过滤状态栏等噪声。
 */
class OcrManager : AutoCloseable {

    companion object {
        private const val TAG = "OcrManager"
    }

    private val recognizer: TextRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /**
     * 识别图片中的文字，返回带坐标的结构化行列表
     *
     * @param context Context
     * @param imageUri 图片 URI
     * @return Result 包含 List<OcrTextLine>，失败时包含异常
     */
    suspend fun recognizeText(
        context: Context,
        imageUri: Uri
    ): Result<List<OcrTextLine>> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val imageWidth = inputImage.width
            val imageHeight = inputImage.height
            Log.d(TAG, "Image size: ${imageWidth}x${imageHeight}")

            val recognizedText = Tasks.await(recognizer.process(inputImage))

            val lines = mutableListOf<OcrTextLine>()

            // 遍历 TextBlock → TextLine，提取文本和边界框
            for (block in recognizedText.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.trim()
                    if (text.isBlank()) continue

                    val box = line.boundingBox
                    val rectF = if (box != null) {
                        RectF(
                            box.left.toFloat(),
                            box.top.toFloat(),
                            box.right.toFloat(),
                            box.bottom.toFloat()
                        )
                    } else {
                        // ML Kit 偶尔返回 null boundingBox，用 block 的兜底
                        val blockBox = block.boundingBox
                        if (blockBox != null) {
                            RectF(
                                blockBox.left.toFloat(),
                                blockBox.top.toFloat(),
                                blockBox.right.toFloat(),
                                blockBox.bottom.toFloat()
                            )
                        } else {
                            RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
                        }
                    }

                    lines.add(OcrTextLine(
                        text = text,
                        boundingBox = rectF,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight
                    ))
                }
            }

            Log.d(TAG, "Recognized ${lines.size} lines")
            // 输出调试日志：每行的坐标和文本
            lines.forEachIndexed { i, line ->
                Log.d(TAG, "  [$i] top=${String.format("%.3f", line.normalizedTop)} " +
                    "bot=${String.format("%.3f", line.normalizedBottom)} " +
                    "text=\"${line.text}\"")
            }

            Result.success(lines)
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            Result.failure(e)
        }
    }

    override fun close() {
        recognizer.close()
    }
}
