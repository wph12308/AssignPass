package com.example.assignpass.ocr

import android.graphics.RectF

/**
 * OCR 识别出的单行文本（带空间坐标信息）
 *
 * @param text 文本内容
 * @param boundingBox 文本在图片中的边界框（像素坐标）
 * @param imageWidth 原图宽度（用于归一化）
 * @param imageHeight 原图高度（用于归一化）
 * @param confidence ML Kit 不提供行级 confidence，此处保留扩展
 */
data class OcrTextLine(
    val text: String,
    val boundingBox: RectF,
    val imageWidth: Int,
    val imageHeight: Int
) {
    /** 顶部 Y 坐标归一化值 (0.0 = 图片顶部, 1.0 = 图片底部) */
    val normalizedTop: Float
        get() = if (imageHeight > 0) boundingBox.top / imageHeight else 0f

    /** 底部 Y 坐标归一化值 */
    val normalizedBottom: Float
        get() = if (imageHeight > 0) boundingBox.bottom / imageHeight else 1f

    /** 左侧 X 坐标归一化值 */
    val normalizedLeft: Float
        get() = if (imageWidth > 0) boundingBox.left / imageWidth else 0f

    /** 右侧 X 坐标归一化值 */
    val normalizedRight: Float
        get() = if (imageWidth > 0) boundingBox.right / imageWidth else 1f

    /** 是否位于图片顶部状态栏区域（顶部 6%） */
    val isInStatusBar: Boolean
        get() = normalizedTop < STATUS_BAR_THRESHOLD

    companion object {
        /** 状态栏占图片高度的比例阈值 */
        const val STATUS_BAR_THRESHOLD = 0.06f
    }
}
