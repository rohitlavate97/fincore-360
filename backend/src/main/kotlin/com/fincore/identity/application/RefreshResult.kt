package com.fincore.identity.application

import com.fincore.identity.domain.User

sealed class RefreshResult {
    data class Success(val newRefreshToken: String, val user: User) : RefreshResult()
    data object ReuseDetected : RefreshResult()
    data object Expired : RefreshResult()
    data object Invalid : RefreshResult()
    data object UserLocked : RefreshResult()
}
