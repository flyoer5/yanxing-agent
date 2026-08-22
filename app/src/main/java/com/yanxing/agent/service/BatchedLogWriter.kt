package com.yanxing.agent.service

import com.yanxing.agent.data.ActionLogEntity
import com.yanxing.agent.data.ActionStatus
import com.yanxing.agent.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 批量异步日志写入器
 * 解决频繁数据库写入的性能问题
 */
class BatchedLogWriter(
    private val repository: ChatRepository,
) {

    // 待写入队列
    private val logQueue = ConcurrentLinkedQueue<PendingActionLog>()

    // 当前批处理状态
    private val _bufferSize = MutableStateFlow(0)
    val bufferSize: StateFlow<Int> = _bufferSize.asStateFlow()

    // 定时刷新调度
    private var refreshJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // 配置参数
    private val BATCH_SIZE = 10            // 每批写入数量
    private val REFRESH_INTERVAL_MS = 500L // 定时刷新间隔
    private val MAX_BATCH_RETRY = 3        // 单批最大重试次数，避免 DB 持续失败时无限重试

    init {
        startBatchProcessor()
    }

    /**
     * 添加日志到队列（立即返回，不阻塞）
     */
    fun addLog(
        packageName: String,
        actionType: String,
        targetElement: String?,
        details: String,
        status: ActionStatus,
        errorMessage: String? = null,
    ) {
        val pendingLog = PendingActionLog(
            packageName = packageName,
            actionType = actionType,
            targetElement = targetElement?.take(200),
            details = details.take(1000),
            status = when (status) {
                is ActionStatus.Completed ->
                    if (status.successCount == status.totalCount) "success" else "failed"
                is ActionStatus.Executing -> "running"
                is ActionStatus.PendingConfirm.Canceled -> "cancelled"
                else -> "unknown"
            },
            errorMessage = errorMessage,
            timestamp = System.currentTimeMillis(),
        )

        logQueue.add(pendingLog)
        _bufferSize.value = logQueue.size

        // 如果达到批量大小，立即触发写入
        if (logQueue.size >= BATCH_SIZE) {
            tryFlushBatch()
        }
    }

    /**
     * 强制刷新所有缓存（排空整个队列，而非只刷一批）
     */
    suspend fun forceFlush() {
        while (logQueue.isNotEmpty()) {
            if (!flushOneBatch()) break // 持续失败则放弃，避免死循环
        }
        _bufferSize.value = logQueue.size
    }

    /**
     * 停止处理器并同步排空剩余数据。
     * shutdown 后进程可能立即退出，必须 runBlocking 等待落库完成，否则丢日志。
     */
    fun shutdown() {
        refreshJob?.cancel()
        runBlocking { forceFlush() }
    }

    /**
     * 启动后台批处理器
     */
    private fun startBatchProcessor() {
        refreshJob = scope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)

                if (logQueue.isNotEmpty()) {
                    tryFlushBatch()
                }
            }
        }
    }

    /**
     * 尝试异步刷新一批数据
     */
    private fun tryFlushBatch() {
        scope.launch { flushOneBatch() }
    }

    /**
     * 取一批写库；成功返回 true。
     * 失败重试超过上限后丢弃本批（日志不应拖垮主流程）。
     */
    private suspend fun flushOneBatch(): Boolean {
        var retry = 0
        while (retry < MAX_BATCH_RETRY) {
            val batch = mutableListOf<PendingActionLog>()
            for (i in 0 until BATCH_SIZE) {
                val item = logQueue.poll() ?: break
                batch.add(item)
            }
            if (batch.isEmpty()) return true

            val entities = batch.map { log ->
                ActionLogEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = log.timestamp,
                    packageName = log.packageName,
                    actionType = log.actionType,
                    targetElement = log.targetElement,
                    details = log.details,
                    status = log.status,
                    errorMessage = log.errorMessage,
                )
            }

            try {
                repository.addBatchActionLogs(entities)
                _bufferSize.value = logQueue.size
                return true
            } catch (e: Exception) {
                retry++
                // 放回队列重试
                batch.forEach { logQueue.add(it) }
                if (retry < MAX_BATCH_RETRY) delay(REFRESH_INTERVAL_MS)
            }
        }
        return false
    }

    data class PendingActionLog(
        val packageName: String,
        val actionType: String,
        val targetElement: String?,
        val details: String,
        val status: String,
        val errorMessage: String?,
        val timestamp: Long,
    )
}
