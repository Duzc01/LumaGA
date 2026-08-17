package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.FetchNotificationRequest
import com.bugenzhao.mnga.protos.service.FetchNotificationResponse
import com.bugenzhao.mnga.protos.datamodel.Notification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Polls the notification list on a timer and exposes the unread badge count,
 * ported from `Models/NotificationModel.swift`. Built on [PagingDataSource]
 * pinned to a single page, like the iOS subclass.
 */
class NotificationModel(scope: CoroutineScope) {

    companion object {
        var shared: NotificationModel? = null
    }

    val dataSource = PagingDataSource<FetchNotificationResponse, Notification>(
        scope = scope,
        responseParser = { FetchNotificationResponse.parser() },
        buildRequest = { _ ->
            AsyncRequest.newBuilder()
                .setFetchNotification(FetchNotificationRequest.getDefaultInstance())
                .build()
        },
        onResponse = { response -> Pair(response.notisList, 1) },
        id = { it.id },
    )

    val showingFromUserMenu = MutableStateFlow(false)
    val showingSheet = MutableStateFlow(false)

    val items: List<Notification> get() = dataSource.items
    val unreadCount: Int get() = items.count { !it.read }

    private val _unreadCountAnimated = MutableStateFlow(0)
    val unreadCountAnimated: StateFlow<Int> = _unreadCountAnimated

    init {
        // Poll: 10 s in debug, 60 s in release; immediate first tick.
        val interval = if (com.bugenzhao.mnga.BuildConfig.DEBUG) 10_000L else 60_000L
        scope.launch {
            while (true) {
                refreshNotis()
                delay(interval)
            }
        }
        scope.launch {
            dataSource.state.collect { state ->
                val count = state.items.count { !it.read }
                if (_unreadCountAnimated.value != count) _unreadCountAnimated.value = count
            }
        }
    }

    fun refreshNotis() {
        appScope.launch {
            val oldCount = unreadCount
            dataSource.refresh(silentOnError = true).join()
            if (unreadCount > oldCount) {
                ToastModel.showAuto(ToastModel.Message.Notification(unreadCount - oldCount))
            }
        }
    }
}
