package com.bugenzhao.mnga.util

/** App-wide constants, ported from `Utilities/Constants.swift`. */
object Constants {
    /**
     * The `mnga://` deep-link scheme, kept under its original name so links
     * stay interchangeable with the MNGA app. Do not rebrand these values.
     */
    object MNGA {
        const val scheme = "mnga"
        const val topicBase = "mnga://topic/"
        const val postBase = "mnga://post/"
        const val forumFBase = "mnga://forum/f/"
        const val forumSTBase = "mnga://forum/st/"
        const val userBase = "mnga://user/"
    }

    object Key {
        /** Persisted store name; renaming it would orphan existing data. */
        const val groupStore = "group.com.bugenzhao.MNGA"
        const val favoriteForums = "favoriteForums"
    }

    /** NGA page size, used by jump/page math. */
    const val postPerPage = 20
}
