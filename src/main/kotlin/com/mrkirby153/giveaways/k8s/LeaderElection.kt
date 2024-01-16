package com.mrkirby153.giveaways.k8s

import com.google.gson.reflect.TypeToken
import com.mrkirby153.botcore.utils.SLF4J
import io.kubernetes.client.custom.V1Patch
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoordinationV1Api
import io.kubernetes.client.openapi.models.V1Lease
import io.kubernetes.client.openapi.models.V1LeaseSpec
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.util.PatchUtils
import io.kubernetes.client.util.Watch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Random


private enum class State {
    LEADER, FOLLOWER, UNKNOWN
}

private val random = Random()

class LeaderElection(
    private val resourceName: String,
    private val nodeName: String,
    private val ttl: Long = 30L,
    private val namespace: String = "default",
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val log by SLF4J

    private val coordinationApi = CoordinationV1Api()

    /**
     * A list of callbacks that are ran when this node becomes a leader
     */
    private val becomeLeaderCallbacks = mutableListOf<suspend () -> Unit>()

    /**
     * A list of callbacks that are ran when this node is no longer a leader
     */
    private val demotedCallbacks = mutableListOf<suspend () -> Unit>()

    /**
     * The current state of this node
     */
    private var state: State = State.UNKNOWN

    /**
     * The job that is responsible for watching for changes
     */
    private var watchJob: Job? = null

    /**
     * The job to refresh the leader's
     */
    private var refreshTtlJob: Job? = null

    /**
     * The job to start an election
     */
    private var followerElectionJob: Job? = null


    /**
     * Initializes the leader election
     */
    fun init() {
        coroutineScope.launch {
            elect()
        }
    }


    /**
     * A [callback] that is executed when this node becomes the leader
     */
    fun onBecomeLeader(callback: () -> Unit) {
        this.becomeLeaderCallbacks.add(callback)
    }

    /**
     * A [callback] that is executed when this node is no longer the leader
     */
    fun onDemote(callback: () -> Unit) {
        this.demotedCallbacks.add(callback)
    }


    private suspend fun elect(attempts: Int = 0) {
        if (attempts > 10) {
            error("Unable to elect a leader after 10 attempts")
        }
        try {
            log.debug("Initializing leader election attempt ${attempts + 1}")
            reset()
            delay(random.nextLong(500)) // Jitter to hopefully avoid races

            val currentLease = withContext(Dispatchers.IO) { getLease() }
            if (currentLease == null) {
                log.debug("Lease does not exist. Creating and acquiring")
                val created = createLease()
                onLead()
                this.state = State.LEADER
            } else {
                if (canAcquireLease(currentLease)) {
                    log.debug("Lease is stale, acquiring...")
                    acquireLease(currentLease)
                    onLead()
                    this.state = State.LEADER
                } else {
                    log.debug("Current leader is ${currentLease.spec?.holderIdentity}. Following...")
                    onFollow()
                    this.state = State.FOLLOWER
                }
            }

            log.debug("Leader election completed")
        } catch (e: Exception) {
            log.error("Error during leader election, retrying in 1 second", e)
            coroutineScope.launch {
                delay(1000)
                elect(attempts + 1)
            }
        }
    }

    private fun canAcquireLease(lease: V1Lease): Boolean {
        val holder = lease.spec?.holderIdentity ?: return true
        val renewTime = lease.spec?.renewTime ?: return true
        if (holder != this.nodeName) {
            // Check if the lease is expired
            val expiresAt =
                renewTime.plusSeconds(lease.spec?.leaseDurationSeconds?.toLong() ?: return true)
            return expiresAt.isBefore(OffsetDateTime.now())
        }
        return false
    }

    private fun getLease(): V1Lease? {
        log.trace("Retrieving lease ${this.resourceName} in ${this.namespace}")
        try {
            return coordinationApi.readNamespacedLease(this.resourceName, this.namespace, null)
        } catch (e: ApiException) {
            if (e.code == 404) {
                log.trace("Resource not found. ${e.responseBody}")
                return null
            }
            error("Caught API Exception ${e.code}: ${e.responseBody}")
        }
    }

    fun expiresAt(): OffsetDateTime {
        val lease = getLease()
        return lease?.spec?.renewTime?.plusSeconds(
            lease.spec?.leaseDurationSeconds?.toLong() ?: 0L
        ) ?: return OffsetDateTime.now()
    }

    private fun createLease(): V1Lease {
        val newLease = V1Lease().apply {
            spec = V1LeaseSpec().apply {
                acquireTime = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MICROS)
                renewTime = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MICROS)
                holderIdentity = this@LeaderElection.nodeName
                leaseDurationSeconds = ttl.toInt()
            }
            metadata = V1ObjectMeta().apply {
                name = this@LeaderElection.resourceName
                namespace = this@LeaderElection.namespace
            }
        }
        return coordinationApi.createNamespacedLease(
            this.namespace, newLease, null, null, null, null
        )
    }

    private fun acquireLease(lease: V1Lease) {
        val acquireTime = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MICROS)
        val renewTime = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MICROS)
        val holderIdentity = this@LeaderElection.nodeName

        val patch = makePatch(
            buildReplaceOp("/spec/acquireTime", acquireTime.toString()),
            buildReplaceOp("/spec/renewTime", renewTime.toString()),
            buildReplaceOp("/spec/holderIdentity", holderIdentity)
        )

        PatchUtils.patch(V1Lease::class.java, {
            coordinationApi.patchNamespacedLeaseCall(
                lease.metadata?.name,
                lease.metadata?.namespace,
                V1Patch(patch),
                null,
                null,
                null,
                null,
                null,
                null
            )
        }, V1Patch.PATCH_FORMAT_JSON_PATCH, coordinationApi.apiClient)
    }

    private fun refreshLease(lease: V1Lease) {
        check(lease?.spec?.holderIdentity == this.nodeName) { "Invariant: Refusing to refresh a lease we do not own" }
        val patch = makePatch(
            buildReplaceOp(
                "/spec/renewTime",
                OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MICROS).toString()
            )
        )
        PatchUtils.patch(V1Lease::class.java, {
            coordinationApi.patchNamespacedLeaseCall(
                lease.metadata?.name,
                lease.metadata?.namespace,
                V1Patch(patch),
                null,
                null,
                null,
                null,
                null,
                null
            )
        }, V1Patch.PATCH_FORMAT_JSON_PATCH, coordinationApi.apiClient)
    }

    private fun onLead() {
        becomeLeaderCallbacks.forEach {
            coroutineScope.launch { it() }
        }
        launchLeaseRefresh()
        watch(true)
    }

    private fun onFollow() {
        if (state == State.LEADER) {
            // Transitioning from leader -> follower, run callbacks
            demotedCallbacks.forEach {
                coroutineScope.launch {
                    it()
                }
            }
        }
        refreshTtlJob?.cancel()
        watch(false)
        launchElectionTimer(expiresAt())
    }


    private fun launchLeaseRefresh() {
        refreshTtlJob?.cancel()
        refreshTtlJob = coroutineScope.launch {
            log.debug("Refreshing lease every $ttl seconds")
            while (true) {
                log.debug("Refreshing lease")
                val lease = getLease() ?: error("Lease not found")
                try {
                    refreshLease(lease)
                    log.debug("Refreshed lease")
                } catch (e: Exception) {
                    log.error("Could not refresh lease", e)
                    delay(5000)
                }
                delay((ttl - 1) * 1000) // Renew the lease 1 second before it expires
            }
        }
    }

    private fun watch(leader: Boolean) {
        watchJob?.cancel()
        watchJob = coroutineScope.launch {
            delay(1000)
            log.debug("Watching for changes. Leader? $leader")

            var running = true
            while (running) {
                try {
                    val watch = Watch.createWatch<V1Lease>(
                        coordinationApi.apiClient, coordinationApi.listNamespacedLeaseCall(
                            namespace,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            true,
                            null
                        ), object : TypeToken<Watch.Response<V1Lease>>() {}.type
                    )

                    watch.use {
                        for (item in watch) {
                            if (!isActive) {
                                return@launch
                            }
                            log.trace("Processing ${item.type}: ${item.`object`?.metadata?.name}")
                            if (item.`object`?.metadata?.name != resourceName) {
                                continue // Ignore watches that are not ourselves
                            }

                            when (item.type) {
                                "ADDED" -> {}
                                "MODIFIED" -> {
                                    handleWatchEvent(item.`object`)
                                }

                                "DELETED" -> {
                                    log.warn("Lease $resourceName was deleted! Re-starting leader election")
                                    launch {
                                        elect()
                                    }
                                    running = false
                                    break
                                }

                                else -> log.warn("Unknown watch type ${item.type} for ${item.`object`?.metadata?.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error occurred watching, restarting", e)
                    delay(1000)
                }
            }
            log.debug("Watch stopping...")
        }
    }

    private fun handleWatchEvent(newObject: V1Lease) {
        // Check if we've been demoted
        if (state == State.LEADER) {
            if (newObject.spec?.holderIdentity != nodeName) {
                log.debug("Demoted from watch, switching to follow")
                onFollow()
                state = State.FOLLOWER
                return
            }
        }
        if (state == State.FOLLOWER) {
            // Check if we've been promoted
            if (newObject.spec?.holderIdentity == nodeName) {
                log.debug("Promoted from watch, switching to leader")
                onLead()
                state = State.LEADER
                return
            }

            // Refresh our election schedule
            val expiresAt = newObject.spec?.renewTime?.plusSeconds(
                newObject.spec?.leaseDurationSeconds?.toLong() ?: 0L
            )
        }
    }

    private fun launchElectionTimer(expires: OffsetDateTime) {
        followerElectionJob?.cancel()
        followerElectionJob = coroutineScope.launch {
            val waitTime = expires.toEpochSecond() - OffsetDateTime.now().toEpochSecond() + 1
            log.debug("Scheduling election in $waitTime seconds")
            delay(waitTime * 1000)
            log.debug("Starting leader election")
            elect()
        }
    }

    private fun reset() {
        followerElectionJob?.cancel()
        watchJob?.cancel()
        refreshTtlJob?.cancel()
        followerElectionJob = null
        watchJob = null
        refreshTtlJob = null
    }

    private fun buildReplaceOp(path: String, value: String): String {
        return "{\"op\": \"replace\", \"path\": \"$path\", \"value\":\"$value\"}"
    }

    private fun makePatch(vararg ops: String): String {
        return "[${ops.joinToString(",")}]"
    }

}