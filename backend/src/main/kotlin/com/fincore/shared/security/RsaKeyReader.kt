package com.fincore.shared.security

import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object RsaKeyReader {

    fun readPublic(pem: String): RSAPublicKey {
        val clean = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val decoded = Base64.getDecoder().decode(clean)
        val spec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }

    fun readPrivate(pem: String): RSAPrivateKey {
        val clean = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val decoded = Base64.getDecoder().decode(clean)
        val spec = PKCS8EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateKey
    }

    fun toPem(publicKey: RSAPublicKey): String {
        val encoded = Base64.getEncoder().encodeToString(publicKey.encoded)
        return "-----BEGIN PUBLIC KEY-----\n" +
                encoded.chunked(64).joinToString("\n") +
                "\n-----END PUBLIC KEY-----"
    }

    fun toPem(privateKey: RSAPrivateKey): String {
        val encoded = Base64.getEncoder().encodeToString(privateKey.encoded)
        return "-----BEGIN PRIVATE KEY-----\n" +
                encoded.chunked(64).joinToString("\n") +
                "\n-----END PRIVATE KEY-----"
    }
}
