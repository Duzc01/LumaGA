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

    /**
     * The GitHub repository releases are published to; the update check reads
     * its latest release. Kept in sync with `scripts/create-release.sh`, which
     * tags `v<versionName>` and attaches the signed APK as a release asset.
     */
    object GitHub {
        const val repo = "Duzc01/LumaGA"
        const val repoUrl = "https://github.com/$repo"
        const val releasesUrl = "$repoUrl/releases"
        const val latestReleaseApi = "https://api.github.com/repos/$repo/releases/latest"
    }

    object Key {
        /** Persisted store name; renaming it would orphan existing data. */
        const val groupStore = "group.com.bugenzhao.MNGA"
        const val favoriteForums = "favoriteForums"
    }

    /** NGA page size, used by jump/page math. */
    const val postPerPage = 20
}
