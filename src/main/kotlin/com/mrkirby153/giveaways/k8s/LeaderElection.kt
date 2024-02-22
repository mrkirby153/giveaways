package com.mrkirby153.giveaways.k8s

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoordinationV1Api
import io.kubernetes.client.openapi.models.V1Lease
import io.kubernetes.client.openapi.models.V1LeaseSpec
import io.kubernetes.client.openapi.models.V1ObjectMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Random

private val random = Random()

private val log = KotlinLogging.logger { }

/**
 * A relatively simple leader election system utilizing kubernetes Leases.
 */
class LeaderElection(
    /**
     * The name of the lock to acquire
     */
    private val resourceName: String,
    /**
     * The identifier to use for leader election
     */
    private val identifier: String,
    /**
     * The namespace this Lease should be created in
     */
    private val namespace: String = "default",
    /**
     * A [CoroutineScope] for executing callbacks
     */
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    /**
     * How long the lease should last
     */
    private val leaseDuration: Long = 30,
    /**
     * The period that the client will use to renew
     */
    private val renewDeadline: Long = 25,
    /**
     * The delay between actions
     */
    private val retryPeriod: Long = 2,

    /**
     * The API client to use. If not specified, the default one will be used
     */
    apiClient: ApiClient? = null
) {

    private val api = if (apiClient != null) CoordinationV1Api(apiClient) else CoordinationV1Api()

    /**
     * Callbacks executed when this node starts leading
     */
    private val onStartLeadingCallbacks = mutableListOf<suspend () -> Unit>()

    /**
     * Callbacks executed when this node stops leading
     */
    private val onStoppedLeadingCallbacks = mutableListOf<suspend () -> Unit>()

    /**
     * Callbacks executed when the leader changes
     */
    private val onNewLeaderCallbacks = mutableListOf<suspend (String) -> Unit>()

    private var running = true

    private var isLeader = false

    init {
        check(identifier.isNotEmpty()) { "Identifier must be specified" }
        log.info { "Created LeaderElection $namespace/$resourceName and identifier $identifier" }
    }

    /**
     * A [callback] executed when this node starts leading
     */
    fun onStartLeading(callback: suspend () -> Unit) {
        this.onStartLeadingCallbacks.add(callback)
    }

    /**
     * A [callback] executed when this node stops leading
     */
    fun onStoppedLeading(callback: suspend () -> Unit) {
        this.onStoppedLeadingCallbacks.add(callback)
    }

    /**
     * A [callback] executed when the leader changes
     */
    fun onNewLeader(callback: suspend (String) -> Unit) {
        this.onNewLeaderCallbacks.add(callback)
    }


    /**
     * Run the leader election process
     */
    suspend fun run() {
        while (running) {
            if (isLeader) {
                // We're already the leader, attempt to renew our lease
                if (!tryRenew()) {
                    log.trace { "${"Leadership lost"}" }
                    isLeader = false
                    // We lost our leadership
                    onNewLeaderCallbacks.forEach {
                        coroutineScope.launch {
                            it(getActualLeader()!!)
                        }
                    }
                    onStoppedLeadingCallbacks.forEach {
                        coroutineScope.launch {
                            it()
                        }
                    }
                }
            } else {
                tryAcquire()
                isLeader = true
                onNewLeaderCallbacks.forEach {
                    coroutineScope.launch {
                        it(getActualLeader()!!)
                    }
                }
                onStartLeadingCallbacks.forEach {
                    coroutineScope.launch {
                        it()
                    }
                }
            }
            delay(jitter(retryPeriod * 1000))
        }
    }

    /**
     * Attempt to acquire the lock
     */
    private suspend fun tryAcquire() {
        log.trace("Attempting to acquire lease")
        while (running) {
            val now = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MICROS)
            val current = getLease()
            if (current == null) {
                log.trace { "${"Current lease does not exist, creating"}" }
                // Lease doesn't exist, create it
                val newLease = V1Lease().apply {
                    metadata = V1ObjectMeta().apply {
                        name = resourceName
                        namespace = this@LeaderElection.namespace
                    }
                    spec = V1LeaseSpec().apply {
                        holderIdentity = identifier
                        leaseDurationSeconds = leaseDuration.toInt()
                        renewTime = now
                        acquireTime = now
                    }
                }
                withContext(Dispatchers.IO) {
                    try {
                        log.trace { "Creating $namespace/$newLease" }
                        api.createNamespacedLease(namespace, newLease, null, null, null, null)
                    } catch (e: Exception) {
                        log.error(e) { "Could not create lease: $namespace/$newLease" }
                        throw e
                    }
                }
                log.debug { "Created and acquired lease" }
                return // Acquired
            } else {
                // Lease exists
                if (isValid(current)) {
                    // Lease is valid, check if we own it
                    if (current.spec?.holderIdentity == identifier) {
                        return // We own the lease
                    } else {
                        log.trace { "lease is owned by someone else: ${current.spec?.holderIdentity}" }
                        delayUntilTime(getEndTime(current))
                    }
                } else {
                    // Lease is invalid, take ownership
                    current.spec!!.apply {
                        renewTime = now
                        holderIdentity = identifier
                        acquireTime = now
                        leaseDurationSeconds = leaseDuration.toInt()
                        leaseTransitions = (current.spec?.leaseTransitions ?: 0) + 1
                    }
                    withContext(Dispatchers.IO) {
                        api.replaceNamespacedLease(
                            current.metadata!!.name,
                            current.metadata!!.namespace,
                            current,
                            null,
                            null,
                            null,
                            null
                        )
                    }
                    log.debug { "Acquired stale lease" }
                    return
                }
            }
            delay(jitter(retryPeriod * 1000))
        }
    }

    private suspend fun tryRenew(): Boolean {
        val now = OffsetDateTime.now(ZoneId.of("UTC"))
        var existingLease = getLease() ?: error("Invariant: Can't renew a lease that doesn't exist")

        if (existingLease.spec?.holderIdentity != identifier) {
            log.trace { "Lease has been acquired by someone else, aborting" }
            // We've lost leadership, abort
            return false
        }

        val renewAt = getRefreshTime(existingLease)
        log.trace { "Renewing at $now" }
        delayUntilTime(renewAt)

        existingLease = getLease() ?: return false

        existingLease.spec!!.renewTime = OffsetDateTime.now(ZoneId.of("UTC"))
        withContext(Dispatchers.IO) {
            api.replaceNamespacedLease(
                existingLease.metadata!!.name,
                existingLease.metadata!!.namespace,
                existingLease,
                null,
                null,
                null,
                null
            )
        }
        log.trace { "Lease renewed to ${existingLease.spec!!.renewTime}" }
        return true
    }

    private suspend fun getLease(): V1Lease? {
        return withContext(Dispatchers.IO) {
            try {
                api.readNamespacedLease(resourceName, namespace, null)
            } catch (e: ApiException) {
                if (e.code == 404) {
                    null
                } else {
                    throw e
                }
            }
        }
    }

    private fun isValid(lease: V1Lease): Boolean {
        val timestamp = lease.spec?.renewTime ?: OffsetDateTime.now(ZoneId.of("UTC"))
        val expiresAt = timestamp.plusSeconds(lease.spec?.leaseDurationSeconds?.toLong() ?: 0L)
        val now = OffsetDateTime.now(ZoneId.of("UTC"))
        log.trace { "Lease expires at $expiresAt, now: $now" }
        return timestamp.plusSeconds(lease.spec?.leaseDurationSeconds?.toLong() ?: 0L).isAfter(
            OffsetDateTime.now(ZoneId.of("UTC"))
        )
    }

    private fun jitter(time: Long): Long {
        return time + random.nextLong(time, (time * 1.5).toLong())
    }

    private suspend fun getActualLeader(): String? {
        return getLease()?.spec?.holderIdentity
    }

    private fun getEndTime(lease: V1Lease): OffsetDateTime {
        val seconds = lease.spec?.leaseDurationSeconds
            ?: error("Invariant: Cannot get the end time of a lease with no duration")
        return lease.spec?.renewTime?.plusSeconds(seconds.toLong())
            ?: error("Invariant: Cannot get the end time of a lease with no renew time")
    }

    private fun getRefreshTime(lease: V1Lease): OffsetDateTime {
        return lease.spec?.renewTime?.plusSeconds(renewDeadline)
            ?: error("Invariant: Cannot get the end time of a lease with no renew time")
    }

    private suspend fun delayUntilTime(time: OffsetDateTime) {
        val delayMs = time.toInstant().toEpochMilli() - System.currentTimeMillis()
        log.trace { "Waiting ${delayMs}ms until $time" }
        delay(delayMs)
    }
}