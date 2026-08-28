package com.fincore.identity.application

import java.security.MessageDigest
import java.util.HexFormat

object TokenHasher {
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(hashBytes)
    }
}
