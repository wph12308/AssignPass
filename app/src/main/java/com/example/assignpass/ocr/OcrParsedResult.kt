package com.example.assignpass.ocr

/**
 * OCR 解析结果 — 存放从截图文字中提取的结构化字段
 */
data class OcrParsedResult(
    val platform: String = "",
    val subject: String = "",
    val name: String = "",
    val deadline: Long? = null,
    val notes: String = ""
) {
    /** 是否完全为空（没有任何识别结果） */
    val isEmpty: Boolean
        get() = platform.isBlank() && subject.isBlank() && name.isBlank()
                && deadline == null && notes.isBlank()
}
