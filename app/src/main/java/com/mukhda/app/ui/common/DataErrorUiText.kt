package com.mukhda.app.ui.common

import com.mukhda.app.R
import com.mukhda.domain.DataError

fun DataError.toUiText(): UiText = UiText.StringResource(
    when (this) {
        DataError.Network.NO_INTERNET -> R.string.error_no_internet
        DataError.Network.REQUEST_TIMEOUT -> R.string.error_timeout
        DataError.Network.TOO_MANY_REQUESTS -> R.string.error_too_many_requests
        DataError.Network.SERVER_ERROR,
        DataError.Network.SERVICE_UNAVAILABLE -> R.string.error_server
        DataError.Local.DISK_FULL -> R.string.error_disk_full
        else -> R.string.error_unknown
    }
)
