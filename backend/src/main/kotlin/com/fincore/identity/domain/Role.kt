package com.fincore.identity.domain

enum class Role(val authority: String) {
    CUSTOMER("ROLE_CUSTOMER"),
    SUPPORT_AGENT("ROLE_SUPPORT_AGENT"),
    OPERATIONS("ROLE_OPERATIONS"),
    AUDITOR("ROLE_AUDITOR"),
    ADMIN("ROLE_ADMIN");

    companion object {
        fun fromAuthority(auth: String): Role? = entries.find { it.authority == auth }
    }
}
