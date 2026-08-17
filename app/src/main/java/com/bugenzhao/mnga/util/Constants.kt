package com.bugenzhao.mnga.util

/** App-wide constants, ported from `Utilities/Constants.swift`. */
object Constants {
    object MNGA {
        const val scheme = "mnga"
        const val topicBase = "mnga://topic/"
        const val postBase = "mnga://post/"
        const val forumFBase = "mnga://forum/f/"
        const val forumSTBase = "mnga://forum/st/"
        const val userBase = "mnga://user/"
    }

    object Key {
        const val groupStore = "group.com.bugenzhao.MNGA"
        const val favoriteForums = "favoriteForums"
    }

    object Plus {
        const val unlockID = "mnga.unlock"
        const val trialID = "mnga.unlock.trial14"
        val ids = listOf(unlockID, trialID)
    }

    /** NGA page size, used by jump/page math. */
    const val postPerPage = 20
}
