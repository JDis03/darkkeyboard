/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dark.keyboard

import android.content.Context
import android.os.AsyncTask
import android.text.format.DateUtils
import android.util.Log
import android.view.inputmethod.InputMethodManager

object LatinIMEUtil {
    /**
     * Cancel an [AsyncTask].
     *
     * @param task the task to cancel
     * @param mayInterruptIfRunning true if the thread executing this
     *        task should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete.
     */
    fun cancelTask(task: AsyncTask<*, *, *>?, mayInterruptIfRunning: Boolean) {
        if (task != null && task.status != AsyncTask.Status.FINISHED) {
            task.cancel(mayInterruptIfRunning)
        }
    }

    class GCUtils {
        private var mGCTryCount = 0

        fun reset() {
            mGCTryCount = 0
        }

        fun tryGCOrWait(metaData: String?, t: Throwable?): Boolean {
            if (mGCTryCount == 0) {
                System.gc()
            }
            if (++mGCTryCount > GC_TRY_COUNT) {
                return false
            } else {
                return try {
                    Thread.sleep(GC_INTERVAL)
                    true
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Sleep was interrupted.")
                    false
                }
            }
        }

        companion object {
            private const val TAG = "GCUtils"
            const val GC_TRY_COUNT = 2
            const val GC_TRY_LOOP_MAX = 5
            private val GC_INTERVAL = DateUtils.SECOND_IN_MILLIS
            private val sInstance = GCUtils()

            @JvmStatic
            fun getInstance(): GCUtils {
                return sInstance
            }
        }
    }

    class RingCharBuffer {
        private var mContext: Context? = null
        private var mEnabled = false
        private var mEnd = 0
        var mLength = 0

        private val mCharBuf = CharArray(BUFSIZE)
        private val mXBuf = IntArray(BUFSIZE)
        private val mYBuf = IntArray(BUFSIZE)

        fun init(context: Context, enabled: Boolean): RingCharBuffer {
            mContext = context
            mEnabled = enabled
            return this
        }

        private fun normalize(inVal: Int): Int {
            val ret = inVal % BUFSIZE
            return if (ret < 0) ret + BUFSIZE else ret
        }

        fun push(c: Char, x: Int, y: Int) {
            if (!mEnabled) return
            mCharBuf[mEnd] = c
            mXBuf[mEnd] = x
            mYBuf[mEnd] = y
            mEnd = normalize(mEnd + 1)
            if (mLength < BUFSIZE) {
                ++mLength
            }
        }

        fun pop(): Char {
            return if (mLength < 1) {
                PLACEHOLDER_DELIMITER_CHAR
            } else {
                mEnd = normalize(mEnd - 1)
                --mLength
                mCharBuf[mEnd]
            }
        }

        fun getLastChar(): Char {
            return if (mLength < 1) {
                PLACEHOLDER_DELIMITER_CHAR
            } else {
                mCharBuf[normalize(mEnd - 1)]
            }
        }

        fun getPreviousX(c: Char, back: Int): Int {
            val index = normalize(mEnd - 2 - back)
            return if (mLength <= back || c.lowercaseChar() != mCharBuf[index].lowercaseChar()) {
                INVALID_COORDINATE
            } else {
                mXBuf[index]
            }
        }

        fun getPreviousY(c: Char, back: Int): Int {
            val index = normalize(mEnd - 2 - back)
            return if (mLength <= back || c.lowercaseChar() != mCharBuf[index].lowercaseChar()) {
                INVALID_COORDINATE
            } else {
                mYBuf[index]
            }
        }

        fun getLastString(): String {
            val sb = StringBuilder()
            for (i in 0 until mLength) {
                val c = mCharBuf[normalize(mEnd - 1 - i)]
                if (c.isLetterOrDigit()) {
                    sb.append(c)
                } else {
                    break
                }
            }
            return sb.reverse().toString()
        }

        fun reset() {
            mLength = 0
        }

        companion object {
            private const val PLACEHOLDER_DELIMITER_CHAR = '\uFFFC'
            private const val INVALID_COORDINATE = -2
            const val BUFSIZE = 20
            private val sRingCharBuffer = RingCharBuffer()

            @JvmStatic
            fun getInstance(): RingCharBuffer {
                return sRingCharBuffer
            }
        }
    }
}
