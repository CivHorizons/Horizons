package net.civhorizons.horizons.util

import com.hypixel.hytale.server.core.universe.datastore.DiskDataStore
import java.util.UUID

/**
 * Extension functions for DiskDataStore to allow using UUIDs as keys directly.
 */

fun <T> DiskDataStore<T>.load(uuid: UUID): T? {
    return try {
        this.load(uuid.toString())
    } catch (e: Exception) {
        null
    }
}

fun <T> DiskDataStore<T>.loadAllUUIDs(): Map<UUID, T> {
    return this.loadAll().mapKeys { (key, _) -> UUID.fromString(key) }
}

fun <T> DiskDataStore<T>.save(uuid: UUID, value: T) {
    this.save(uuid.toString(), value)
}

fun <T> DiskDataStore<T>.saveAll(objectsToSave: Map<UUID, T>) {
    objectsToSave.forEach { uuid, t ->
        this.save(uuid.toString(), t)
    }
}

fun <T> DiskDataStore<T>.remove(uuid: UUID) {
    try {
        this.remove(uuid.toString())
    } catch (e: Exception) {
        // Log or handle as needed
    }
}

fun <T> DiskDataStore<T>.listUUIDs(): List<UUID> {
    return this.list().mapNotNull { 
        try {
            UUID.fromString(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
