package com.yanxing.agent

import android.app.Application
import com.yanxing.agent.data.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@HiltAndroidApp
class YanxingApplication : Application() {

    @Inject lateinit var database: AppDatabase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { stripLegacyAttachmentBase64() }
    }

    /**
     * 一次性清洗：旧版本把附件 base64 存进了 messages 表（每条带图消息膨胀数 MB）。
     * 171 版起附件只存元数据，这里把存量行里的 base64 字段剥掉，显著缩小数据库。
     * 幂等，仅执行一次后打标。
     */
    private suspend fun stripLegacyAttachmentBase64() {
        val prefs = getSharedPreferences("maintenance", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_BASE64_CLEANED, false)) return

        var cleaned = 0
        runCatching {
            val rows = database.messageDao().findMessagesWithLegacyBase64()
            for (row in rows) {
                val rewritten = JSONArray().apply {
                    val arr = JSONArray(row.attachments)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.has("base64")) {
                            obj.remove("base64")
                            cleaned++
                        }
                        put(obj)
                    }
                }.toString()
                database.messageDao().updateAttachments(row.id, rewritten)
            }
        }
        prefs.edit().putBoolean(KEY_LEGACY_BASE64_CLEANED, true).apply()
        if (cleaned > 0) {
            android.util.Log.i("YanxingMaintenance", "已清洗 $cleaned 条历史附件的 base64 数据")
        }
    }

    private companion object {
        const val KEY_LEGACY_BASE64_CLEANED = "legacy_base64_cleaned"
    }
}
