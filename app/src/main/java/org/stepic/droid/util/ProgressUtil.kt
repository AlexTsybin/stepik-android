package org.stepic.droid.util

import org.stepik.android.model.Progress
import org.stepik.android.model.Progressable

object ProgressUtil {
    fun getProgresses(objects: List<Progressable>?): Array<String> {
        return objects
                ?.mapNotNull { it.progress }
                ?.toTypedArray()
                ?: emptyArray()
    }

    fun getProgressPercent(progress: Progress?): Int? {
        if (progress == null) {
            return null
        }

        val score: Double? = progress.score?.let { StringUtil.safetyParseString(it) }
        val cost = progress.cost
        return if (score != null) {
            val progressPart: Double = score / cost
            val progressShow: Int = (progressPart * 100).toInt()
            progressShow
        } else {
            null
        }
    }
}
