package com.example.svoi.util

import com.example.svoi.data.model.HiddenOnlineStyle
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import com.example.svoi.data.model.isTrulyOnline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object OnlineStatusFormatter {
    private val mysteryStatuses = listOf(
        "[???]",
        "~~~~~",
        "#--#--#",
        "⬤⬤⬤",
        "¿¡¿¡¿",
        "#_!#$^"
    )

    fun format(
        presence: UserPresence?,
        profile: Profile?,
        viewerUserId: String? = null,
        viewedUserId: String? = profile?.id
    ): String? {
        val hideForViewer = shouldHideForViewer(profile, viewerUserId, viewedUserId)
        if (presence == null) {
            if (!hideForViewer) return null
            return when (HiddenOnlineStyle.fromDb(profile?.hiddenOnlineStyle)) {
                HiddenOnlineStyle.APPROXIMATE -> "○○○ Нет сигнала"
                HiddenOnlineStyle.MYSTERY -> mysteryStatus(viewedUserId ?: profile?.id ?: "unknown")
            }
        }
        if (!hideForViewer) {
            return when {
                presence.isTrulyOnline() -> "в сети"
                !presence.lastSeen.isNullOrBlank() -> presence.lastSeen.toLastSeen()
                else -> null
            }
        }

        return when (HiddenOnlineStyle.fromDb(profile?.hiddenOnlineStyle)) {
            HiddenOnlineStyle.APPROXIMATE -> approximateStatus(presence)
            HiddenOnlineStyle.MYSTERY -> mysteryStatus(viewedUserId ?: presence.userId)
        }
    }

    fun isExactOnlineVisible(
        presence: UserPresence?,
        profile: Profile?,
        viewerUserId: String? = null,
        viewedUserId: String? = profile?.id
    ): Boolean {
        if (presence?.isTrulyOnline() != true) return false
        return !shouldHideForViewer(profile, viewerUserId, viewedUserId)
    }

    private fun shouldHideForViewer(
        profile: Profile?,
        viewerUserId: String?,
        viewedUserId: String?
    ): Boolean {
        if (profile?.hideOnlineStatus != true) return false
        if (!viewerUserId.isNullOrBlank() && viewerUserId == viewedUserId) return false
        return true
    }

    private fun approximateStatus(presence: UserPresence): String {
        val lastSeen = presence.lastSeen?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
        }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()

        return when {
            presence.isTrulyOnline() -> "●●● Активен недавно"
            lastSeen == null -> "○○○ Нет сигнала"
            ChronoUnit.MINUTES.between(lastSeen, now) < 30 -> "●●● Активен недавно"
            lastSeen.atZone(zone).toLocalDate() == LocalDate.now(zone) -> "●●○ Был сегодня"
            else -> "●○○ Был давно"
        }
    }

    private fun mysteryStatus(userId: String): String {
        val dayKey = LocalDate.now(ZoneId.systemDefault()).toString()
        val index = Math.floorMod("$userId:$dayKey".hashCode(), mysteryStatuses.size)
        return mysteryStatuses[index]
    }
}
