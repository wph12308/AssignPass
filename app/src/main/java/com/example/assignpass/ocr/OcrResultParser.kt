package com.example.assignpass.ocr

import android.util.Log
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.min

/**
 * 规则引擎解析器 — 从结构化 OCR 文本行中提取作业字段
 *
 * 核心优化（相比旧版）：
 * 1. 接收 List<OcrTextLine>（带坐标），用 Y 坐标精确过滤状态栏
 * 2. 仅从含截止关键词的行提取日期，杜绝状态栏/页码误匹配
 * 3. 关键词模糊匹配（编辑距离 ≤ 1），容忍 OCR 字符错误
 * 4. 收紧关键词：移除"第"等过于泛化的词
 * 5. 全程 Log 输出解析过程，便于调试
 */
class OcrResultParser {

    /**
     * 解析结构化 OCR 文本行
     *
     * @param ocrLines ML Kit 返回的文本行列表（带坐标）
     */
    fun parse(ocrLines: List<OcrTextLine>): OcrParsedResult {
        if (ocrLines.isEmpty()) return OcrParsedResult()

        Log.d(TAG, "=== 开始解析，共 ${ocrLines.size} 行 ===")

        // 1. 过滤状态栏行（顶部 6%）
        val contentLines = ocrLines.filter { !it.isInStatusBar }
        Log.d(TAG, "过滤状态栏后剩 ${contentLines.size} 行")

        if (contentLines.isEmpty()) {
            Log.w(TAG, "所有行都在状态栏区域，返回空结果")
            return OcrParsedResult()
        }

        // 2. 归一化文本
        val normalizedLines = contentLines.map { line ->
            OcrTextLine(
                text = normalizeOcrErrors(line.text),
                boundingBox = line.boundingBox,
                imageWidth = line.imageWidth,
                imageHeight = line.imageHeight
            )
        }

        val lines = normalizedLines.map { it.text }
        val matchedIndices = mutableSetOf<Int>()

        // 3. 平台检测
        val (platform, platformIdx) = findPlatform(lines)
        if (platformIdx >= 0) matchedIndices.add(platformIdx)
        Log.d(TAG, "平台: \"$platform\" (idx=$platformIdx)")

        // 4. 截止时间检测 —— 仅从含关键词的行提取
        val (deadline, deadlineLines) = findDeadline(normalizedLines)
        deadlineLines.forEach { matchedIndices.add(it) }
        Log.d(TAG, "截止时间: ${deadline?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(it)) }} (lines=$deadlineLines)")

        // 5. 学科检测
        val (subject, subjectIdx) = findSubject(lines, matchedIndices)
        if (subjectIdx >= 0) matchedIndices.add(subjectIdx)
        Log.d(TAG, "学科: \"$subject\" (idx=$subjectIdx)")

        // 6. 作业名称检测
        val (name, nameIdx) = findAssignmentName(lines, matchedIndices)
        if (nameIdx >= 0) matchedIndices.add(nameIdx)
        Log.d(TAG, "作业名: \"$name\" (idx=$nameIdx)")

        // 7. 收集备注
        val notes = buildNotes(lines, matchedIndices)

        Log.d(TAG, "=== 解析结束 ===")

        return OcrParsedResult(
            platform = platform,
            subject = subject,
            name = name,
            deadline = deadline,
            notes = notes
        )
    }

    // ==================== OCR 字符归一化 ====================

    private fun normalizeOcrErrors(text: String): String {
        return text
            .replace("裁止", "截止")
            .replace("载止", "截止")
            .replace("截 止", "截止")
            .replace("截  止", "截止")
            .replace("曰期", "日期")
            .replace('：', ':')
            .replace('　', ' ')
            .replace(Regex("(\\d)\\s+年"), "$1年")
            .replace(Regex("(\\d)\\s+月"), "$1月")
            .replace(Regex("(\\d)\\s+日"), "$1日")
            .replace(Regex("年\\s+(\\d)"), "年$1")
            .replace(Regex("月\\s+(\\d)"), "月$1")
            .replace(Regex("日\\s+(\\d)"), "日$1")
            .replace(Regex("截止\\s+:"), "截止:")
            .replace(Regex("提交\\s*:"), "提交:")
            .replace('０', '0').replace('１', '1').replace('２', '2')
            .replace('３', '3').replace('４', '4').replace('５', '5')
            .replace('６', '6').replace('７', '7').replace('８', '8')
            .replace('９', '9')
    }

    // ==================== 平台检测 ====================

    private fun findPlatform(lines: List<String>): Pair<String, Int> {
        for ((idx, line) in lines.withIndex()) {
            for ((keyword, platformName) in PLATFORM_MAPPINGS) {
                if (fuzzyContains(line, keyword)) {
                    return platformName to idx
                }
            }
        }
        val fullText = lines.joinToString("")
        for ((keyword, platformName) in PLATFORM_FUZZY_MAPPINGS) {
            if (fuzzyContains(fullText, keyword)) {
                return platformName to -1
            }
        }
        return "" to -1
    }

    // ==================== 截止时间检测 ====================

    /**
     * 核心优化：仅从含截止关键词的行中提取日期
     * 如果没有任何含关键词的行命中日期，才退化为从所有内容行提取（但加严格过滤）
     */
    private fun findDeadline(lines: List<OcrTextLine>): Pair<Long?, Set<Int>> {
        val deadlineLineIndices = mutableSetOf<Int>()
        val allCandidates = mutableListOf<DeadlineCandidate>()

        val now = System.currentTimeMillis()
        val lowerBound = now - 86400000L * 365
        val upperBound = now + 86400000L * 365 * 2

        // 第一优先级：从含截止关键词的行提取日期
        for ((idx, line) in lines.withIndex()) {
            val text = line.text
            val hasKeyword = deadlineKeywords.any { kw -> fuzzyContains(text, kw) }
            if (!hasKeyword) continue

            Log.d(TAG, "  发现截止关键词行 [$idx]: \"$text\"")

            for (pi in DATE_PATTERNS.indices) {
                val pattern = DATE_PATTERNS[pi]
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    val timestamp = parseDateMatch(matcher, pi)
                    if (timestamp != null && timestamp > lowerBound && timestamp < upperBound) {
                        Log.d(TAG, "    匹配 pattern[$pi] → ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(timestamp))}")
                        allCandidates.add(DeadlineCandidate(timestamp, idx, pi, hasKeyword = true))
                        deadlineLineIndices.add(idx)
                    }
                }
            }
        }

        // 如果关键词行命中了至少一个日期，直接选最佳的
        if (allCandidates.isNotEmpty()) {
            val best = selectBestDeadline(allCandidates, now)
            Log.d(TAG, "  关键词行命中 ${allCandidates.size} 个候选，选择: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(best))}")
            return best to deadlineLineIndices
        }

        // 退化策略：关键词行没有命中日期，尝试从所有行提取
        // 但排除纯数字行、过短行、以及明显是导航栏/页码的行
        Log.d(TAG, "  关键词行未命中日期，尝试退化策略")
        for ((idx, line) in lines.withIndex()) {
            val text = line.text
            // 排除：纯数字/符号行、长度 < 4 的行
            if (text.length < 4) continue
            if (text.matches(Regex("^[\\d\\s\\-:./]+$"))) continue

            for (pi in DATE_PATTERNS.indices) {
                val pattern = DATE_PATTERNS[pi]
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    val timestamp = parseDateMatch(matcher, pi)
                    if (timestamp != null && timestamp > lowerBound && timestamp < upperBound) {
                        allCandidates.add(DeadlineCandidate(timestamp, idx, pi, hasKeyword = false))
                        deadlineLineIndices.add(idx)
                    }
                }
            }
        }

        if (allCandidates.isEmpty()) return null to emptySet()

        val best = selectBestDeadline(allCandidates, now)
        Log.d(TAG, "  退化策略命中 ${allCandidates.size} 个候选，选择: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(best))}")
        return best to deadlineLineIndices
    }

    /**
     * 从候选列表中选择最佳截止时间
     * 策略：优先取最近的未来日期；如果全是过去日期，取最近的过去日期
     */
    private fun selectBestDeadline(candidates: List<DeadlineCandidate>, now: Long): Long {
        val future = candidates.filter { it.timestamp > now }
        return if (future.isNotEmpty()) {
            future.minBy { it.timestamp }.timestamp
        } else {
            candidates.maxBy { it.timestamp }.timestamp
        }
    }

    /**
     * 根据模式索引解析时间戳，包含年份合理性校验
     */
    private fun parseDateMatch(matcher: java.util.regex.Matcher, patternIndex: Int): Long? {
        return try {
            val cal = Calendar.getInstance()
            val now = Calendar.getInstance()
            val currentYear = now.get(Calendar.YEAR)

            when (patternIndex) {
                // [0] "2025年12月31日 23:59"
                0 -> {
                    cal.set(Calendar.YEAR, matcher.group(1)!!.toInt())
                    cal.set(Calendar.MONTH, matcher.group(2)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(3)!!.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, matcher.group(4)!!.toInt())
                    cal.set(Calendar.MINUTE, matcher.group(5)!!.toInt())
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    sanitizeYear(cal, currentYear)
                    cal.timeInMillis
                }
                // [1] "2025-12-31 23:59"
                1 -> {
                    cal.set(Calendar.YEAR, matcher.group(1)!!.toInt())
                    cal.set(Calendar.MONTH, matcher.group(2)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(3)!!.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, matcher.group(4)!!.toInt())
                    cal.set(Calendar.MINUTE, matcher.group(5)!!.toInt())
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    sanitizeYear(cal, currentYear)
                    cal.timeInMillis
                }
                // [2] "12月31日 23:59"（无年份）
                2 -> {
                    cal.set(Calendar.MONTH, matcher.group(1)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(2)!!.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, matcher.group(3)!!.toInt())
                    cal.set(Calendar.MINUTE, matcher.group(4)!!.toInt())
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis < now.timeInMillis - 86400000L) {
                        cal.add(Calendar.YEAR, 1)
                    }
                    cal.timeInMillis
                }
                // [3] "2025年12月31日"（无时间）
                3 -> {
                    cal.set(Calendar.YEAR, matcher.group(1)!!.toInt())
                    cal.set(Calendar.MONTH, matcher.group(2)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(3)!!.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    sanitizeYear(cal, currentYear)
                    cal.timeInMillis
                }
                // [4] "2025-12-31"（无时间）
                4 -> {
                    cal.set(Calendar.YEAR, matcher.group(1)!!.toInt())
                    cal.set(Calendar.MONTH, matcher.group(2)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(3)!!.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    sanitizeYear(cal, currentYear)
                    cal.timeInMillis
                }
                // [5] "12月31日"（无年份无时间）
                5 -> {
                    cal.set(Calendar.MONTH, matcher.group(1)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(2)!!.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis < now.timeInMillis - 86400000L) {
                        cal.add(Calendar.YEAR, 1)
                    }
                    cal.timeInMillis
                }
                // [6] "2025.12.31 23:59" 或 "2025.12.31"
                6 -> {
                    cal.set(Calendar.YEAR, matcher.group(1)!!.toInt())
                    cal.set(Calendar.MONTH, matcher.group(2)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(3)!!.toInt())
                    val h = matcher.group(4)
                    val m = matcher.group(5)
                    if (h != null && m != null) {
                        cal.set(Calendar.HOUR_OF_DAY, h.toInt())
                        cal.set(Calendar.MINUTE, m.toInt())
                    } else {
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                    }
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    sanitizeYear(cal, currentYear)
                    cal.timeInMillis
                }
                // [7] "12/31" / "12-31"（无年份）—— 仅在退化策略中作为补充
                7 -> {
                    cal.set(Calendar.MONTH, matcher.group(1)!!.toInt() - 1)
                    cal.set(Calendar.DAY_OF_MONTH, matcher.group(2)!!.toInt())
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    if (cal.timeInMillis < now.timeInMillis - 86400000L) {
                        cal.add(Calendar.YEAR, 1)
                    }
                    cal.timeInMillis
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 学科检测 ====================
    //
    // 仅当 OCR 文本中明确出现
    // 已知学科名 或 "课程名称：xxx" 格式的标签时才填充。
    // 否则留空，让用户手动选择。

    private fun findSubject(lines: List<String>, used: Set<Int>): Pair<String, Int> {
        // 第一轮：关键词前缀匹配（"课程名称：高等数学" → "高等数学"）
        for ((idx, line) in lines.withIndex()) {
            if (idx in used) continue
            for (keyword in subjectKeywords) {
                if (fuzzyContains(line, keyword)) {
                    val after = line.substringAfter(keyword).trim()
                    val clean = after.removePrefix(":").trim()
                    if (clean.isNotBlank() && clean.length in 2..30) {
                        return clean to idx
                    }
                    if (after.isBlank() && idx + 1 < lines.size) {
                        val next = lines[idx + 1].trim()
                        if (next.isNotBlank() && next.length in 2..30 &&
                            !fuzzyContainsAny(next, deadlineKeywords + platformKeywords)
                        ) {
                            return next to idx + 1
                        }
                    }
                }
            }
        }

        // 第二轮：与已知学科名列表匹配
        for ((idx, line) in lines.withIndex()) {
            if (idx in used) continue
            val cleanLine = line.trim()
            // 精确匹配或包含匹配
            for (subject in KNOWN_SUBJECTS) {
                if (cleanLine == subject || cleanLine.contains(subject)) {
                    return subject to idx
                }
            }
        }

        // 未找到明确学科，留空
        Log.d(TAG, "学科未匹配到已知名称，留空")
        return "" to -1
    }

    // ==================== 作业名称检测 ====================

    private fun findAssignmentName(lines: List<String>, used: Set<Int>): Pair<String, Int> {
        // 第一轮：关键词前缀匹配（用收紧后的 nameKeywords）
        for ((idx, line) in lines.withIndex()) {
            if (idx in used) continue
            for (keyword in nameKeywords) {
                if (fuzzyContains(line, keyword)) {
                    val after = line.substringAfter(keyword).trim()
                    val clean = after.removePrefix(":").trim()
                    if (clean.isNotBlank() && clean.length in 2..100) {
                        return clean to idx
                    }
                    if (clean.isBlank() && idx + 1 < lines.size) {
                        val next = lines[idx + 1].trim()
                        if (next.isNotBlank() && next.length in 2..100 &&
                            !fuzzyContainsAny(next, deadlineKeywords)
                        ) {
                            return next to idx + 1
                        }
                    }
                }
            }
        }

        // 第二轮：长度启发式
        for ((idx, line) in lines.withIndex()) {
            if (idx in used) continue
            if (line.length in 4..60 &&
                !fuzzyContainsAny(line, deadlineKeywords + subjectKeywords + platformKeywords) &&
                !line.matches(Regex(".*\\d{4}年.*"))
            ) {
                return line to idx
            }
        }

        // 第三轮：第一个未被使用且不是纯数字/日期的行
        for ((idx, line) in lines.withIndex()) {
            if (idx in used) continue
            if (line.length >= 2 &&
                !line.matches(Regex("^[\\d\\s\\-:./年月日关]+$"))
            ) {
                return line to idx
            }
        }

        return "" to -1
    }

    // ==================== 备注收集 ====================

    private fun buildNotes(lines: List<String>, used: Set<Int>): String {
        return lines.filterIndexed { idx, line ->
            idx !in used &&
                line.length > 1 &&
                !line.matches(Regex("^[\\d\\s\\-:./]+$"))
        }.joinToString("\n")
    }

    // ==================== 工具方法 ====================

    /**
     * 校验并修正 OCR 可能误识别的年份
     * 如果日期超过当前时间 1 年以上，逐年减 1 直到落在合理范围内
     */
    private fun sanitizeYear(cal: Calendar, currentYear: Int) {
        val now = Calendar.getInstance()
        val oneYearLater = now.clone() as Calendar
        oneYearLater.add(Calendar.YEAR, 1)
        oneYearLater.add(Calendar.DAY_OF_MONTH, 7)

        while (cal.timeInMillis > oneYearLater.timeInMillis && cal.get(Calendar.YEAR) > currentYear) {
            cal.add(Calendar.YEAR, -1)
        }
    }

    /**
     * 模糊包含匹配：先精确匹配，再用编辑距离 ≤ 1 匹配
     * 用于容忍 OCR 字符识别错误（如"截止"被识别为"裁止"已在归一化处理，
     * 但仍有未覆盖的情况，如"截止"→"截止."等）
     */
    private fun fuzzyContains(line: String, keyword: String): Boolean {
        // 精确匹配（含去空格）
        if (line.contains(keyword)) return true
        val normLine = line.replace(Regex("\\s+"), "")
        val normKw = keyword.replace(Regex("\\s+"), "")
        if (normLine.contains(normKw)) return true

        // 对于长度 ≤ 4 的关键词，用编辑距离匹配
        if (normKw.length <= 4 && normLine.length >= normKw.length) {
            val lowerLine = normLine.lowercase()
            val lowerKw = normKw.lowercase()
            // 滑动窗口检查
            for (i in 0..lowerLine.length - lowerKw.length) {
                val substring = lowerLine.substring(i, i + lowerKw.length)
                if (editDistance(substring, lowerKw) <= 1) {
                    return true
                }
            }
        }
        return false
    }

    private fun fuzzyContainsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { fuzzyContains(text, it) }
    }

    /**
     * 计算两个字符串的编辑距离（Levenshtein distance）
     * 仅用于短字符串（长度 ≤ 10），不做性能优化
     */
    private fun editDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    /**
     * 判断字符串是否以中文字符为主（中文字符占比 > 50%）
     */
    private fun isMostlyChinese(text: String): Boolean {
        val chineseCount = text.count { c ->
            c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf'
        }
        return chineseCount > text.length * 0.5
    }

    // ==================== 内部数据结构 ====================

    private data class DeadlineCandidate(
        val timestamp: Long,
        val lineIdx: Int,
        val patternIdx: Int,
        val hasKeyword: Boolean = false
    )

    companion object {
        private val TAG = "OcrResultParser"

        // ==================== 平台映射 ====================

        val PLATFORM_MAPPINGS = listOf(
            "mis课程平台" to "mis课程平台",
            "MIS" to "mis课程平台",
            "中国大学MOOC" to "中国大学MOOC",
            "MOOC" to "中国大学MOOC",
            "雨课堂" to "雨课堂",
            "U校园" to "U校园",
            "学堂在线" to "学堂在线",
            "知到" to "知到",
            "学习通" to "学习通",
            "超星" to "学习通",
            "步道乐跑" to "步道乐跑",
            "智慧树" to "智慧树",
            "学银在线" to "学银在线",
        )

        val PLATFORM_FUZZY_MAPPINGS = listOf(
            "mooc" to "中国大学MOOC",
            "学堂" to "学堂在线",
            "智慧树" to "智慧树",
            "雨课堂" to "雨课堂",
            "mis" to "mis课程平台",
        )

        // ==================== 关键词列表 ====================

        val platformKeywords = listOf(
            "MOOC", "雨课堂", "U校园", "知到", "学习通", "超星",
            "智慧树", "学堂在线", "步道乐跑", "学银在线", "中国大学MOOC", "mis课程平台"
        )

        /** 截止时间关键词 —— 收紧：移除"提交"（太泛化，如"提交答案"按钮） */
        val deadlineKeywords = listOf(
            "截止", "截止时间", "截止日期",
            "期限", "ddl", "DDL", "deadline", "Deadline",
            "截至", "截稿", "最后期限", "过期", "到期"
        )

        val subjectKeywords = listOf(
            "课程名称", "科目", "学科", "教学班", "班级",
            "课程名", "课程代码", "课程序号", "开课学院", "授课教师"
        )

        /** 已知常见学科名列表 —— 只有匹配到此列表中的名称才自动填充学科 */
        val KNOWN_SUBJECTS = listOf(
            // 基础学科
            "高等数学", "线性代数", "概率论", "概率论与数理统计", "离散数学",
            "大学物理", "大学化学", "大学语文", "大学英语", "英语",
            "计算机基础", "计算机文化基础", "程序设计基础","微积分",
            // 计算机相关
            "数据结构", "算法", "算法设计与分析", "操作系统", "计算机网络",
            "数据库", "数据库系统", "数据库原理", "软件工程", "编译原理",
            "计算机组成原理", "计算机体系结构", "人工智能", "机器学习",
            "深度学习", "自然语言处理", "计算机视觉", "信息安全",
            "网络安全", "密码学", "分布式系统", "云计算", "大数据",
            // 电子信息
            "信号与系统", "数字信号处理", "通信原理", "电磁场",
            "模拟电子技术", "数字电子技术", "电路分析", "微机原理",
            "嵌入式系统", "物联网",
            // 经管类
            "微观经济学", "宏观经济学", "统计学", "计量经济学",
            "管理学", "市场营销", "财务管理", "会计学", "运筹学",
            // 数理类
            "数学分析", "高等代数", "近世代数", "实变函数", "复变函数",
            "拓扑学", "微分几何", "泛函分析", "数值分析", "优化方法",
            // 其他常见
            "马克思主义基本原理", "毛泽东思想", "思想道德修养",
            "形势与政策", "体育", "心理健康", "大学生职业规划",
            "工程制图", "材料力学", "理论力学", "流体力学",
            "机械设计", "自动控制原理", "机器人学"
        )

        /** 作业名关键词 —— 收紧：移除"第"（太泛化）、"提交"等 */
        val nameKeywords = listOf(
            "作业", "任务", "实验", "报告",
            "Assignment", "Homework",
            "测验", "考试", "练习", "习题",
            "Chapter", "Week", "Lab",
            "Project", "Paper", "单元", "模块"
        )

        // ==================== 日期正则模式 ====================

        val DATE_PATTERNS = listOf(
            // [0] "2025年12月31日 23:59"
            Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*(\\d{1,2})\\s*[：:时]\\s*(\\d{2})\\s*分?"),
            // [1] "2025-12-31 23:59"
            Pattern.compile("(\\d{4})\\s*[-/]\\s*(\\d{1,2})\\s*[-/]\\s*(\\d{1,2})\\s*(?:T|\\s+)(\\d{1,2})\\s*[：:]\\s*(\\d{2})"),
            // [2] "12月31日 23:59"
            Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*(\\d{1,2})\\s*[：:]\\s*(\\d{2})"),
            // [3] "2025年12月31日"（无时间）
            Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日(?!\\s*\\d)"),
            // [4] "2025-12-31"（无时间）
            Pattern.compile("(\\d{4})\\s*[-/]\\s*(\\d{1,2})\\s*[-/]\\s*(\\d{1,2})(?!\\s*\\d)"),
            // [5] "12月31日"（无年份无时间）
            Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日(?!\\s*\\d)"),
            // [6] "2025.12.31 23:59" / "2025.12.31"（点分隔）
            Pattern.compile("(\\d{4})\\s*[.-]\\s*(\\d{1,2})\\s*[.-]\\s*(\\d{1,2})(?:\\s+(\\d{1,2})\\s*[：:]\\s*(\\d{2}))?"),
            // [7] "12/31" / "12-31"（无年份）
            Pattern.compile("(\\d{1,2})\\s*[/\\-]\\s*(\\d{1,2})(?!\\s*\\d)"),
        )
    }
}
