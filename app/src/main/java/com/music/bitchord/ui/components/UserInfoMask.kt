package com.music.bitchord.ui.components

import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.data.model.Account

/*
 * Screenshot mode (Advanced options → Hide user info). These stand in for the
 * signed-in identity wherever it is drawn, so a screenshot shows no name,
 * email or photo. They are applied where the UI reads its state, never to the
 * view model, so requests and account switching still see the real values.
 */

/** The name shown in place of every account, profile and linked-service name. */
const val HIDDEN_USER_NAME = "Listener"

/** No photo, so each avatar falls back to its placeholder icon. */
fun Account.masked(): Account = Account(name = HIDDEN_USER_NAME, email = "", thumbnailUrl = null)

/** Numbered so a switcher with several logins still reads as several rows. */
fun List<GoogleAccountSession>.masked(): List<GoogleAccountSession> {
    var n = 0
    return map { session ->
        session.copy(
            name = HIDDEN_USER_NAME,
            email = "",
            profiles = session.profiles.map { profile ->
                n++
                profile.copy(
                    name = if (n == 1) HIDDEN_USER_NAME else "$HIDDEN_USER_NAME $n",
                    handle = "",
                    avatar = null,
                )
            },
        )
    }
}

/** A linked-service username (Last.fm, Discord), hidden when the mode is on. */
fun String.maskedUsername(hide: Boolean): String = if (hide && isNotEmpty()) HIDDEN_USER_NAME else this
