package com.gymbuddy.domain.catalog

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Deterministic catalog identity. PR 14 §2.1:
 * NAMESPACE = uuidv5(RFC4122 DNS, "catalog.gymbuddy.app")
 * id(slug)  = uuidv5(NAMESPACE, slug)
 */
object CatalogIds {
    val DNS_NAMESPACE: UUID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
    val NAMESPACE: UUID = UUID.fromString("a6c06b9c-44ea-5dc4-a9d4-5636bb4b2dc6")

    fun fromSlug(slug: String, namespace: UUID = NAMESPACE): UUID {
        val nsBytes = ByteBuffer.allocate(16)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val sha = MessageDigest.getInstance("SHA-1").digest(nsBytes + slug.toByteArray(Charsets.UTF_8))
        sha[6] = ((sha[6].toInt() and 0x0f) or 0x50).toByte()
        sha[8] = ((sha[8].toInt() and 0x3f) or 0x80).toByte()
        val buf = ByteBuffer.wrap(sha, 0, 16)
        return UUID(buf.long, buf.long)
    }
}
