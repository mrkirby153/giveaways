package com.mrkirby153.giveaways.utils

import com.mrkirby153.botcore.utils.SLF4J
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Handles transaction management in coroutines
 */
class CoroutineTransactionHandler(val template: TransactionTemplate) {

    val log by SLF4J

    /**
     * Runs the given [block] inside of a transaction
     */
    suspend inline fun <T> transaction(crossinline block: suspend CoroutineScope.() -> T): T {
        val existingTransaction = coroutineContext[CoroutineTransaction]
        return when {
            existingTransaction == null -> {
                log.debug("Starting new transaction")
                withContext(CoroutineTransactionTemplate(template)) {
                    runTransactionally {
                        block()
                    }
                }
            }

            existingTransaction.incomplete -> {
                log.debug("Re-using existing transaction")
                withContext(coroutineContext) {
                    block()
                }
            }

            else -> error("Attempted to start new transaction within: $existingTransaction")
        }
    }

    suspend inline fun <T> runTransactionally(crossinline block: suspend CoroutineScope.(TransactionStatus) -> T): T {
        val status = coroutineContext.transactionTemplate.transactionManager?.getTransaction(
            coroutineContext.transactionTemplate
        ) ?: error("Could not start a transaction")
        val transaction = CoroutineTransaction()
        try {
            val result = withContext(transaction) {
                block(status)
            }
            if (status.isRollbackOnly) {
                log.trace("Rolling back transaction due to transaction being set to rollback only")
                coroutineContext.transactionTemplate.transactionManager?.rollback(status)
            } else {
                log.trace("Committing transaction")
                coroutineContext.transactionTemplate.transactionManager?.commit(status)
            }
            return result
        } catch (ex: Throwable) {
            log.error("Caught exception from transactional method, intiating rollback", ex)
            coroutineContext.transactionTemplate.transactionManager?.rollback(status)
            throw ex
        } finally {
            log.trace("Marking transaction as complete")
            transaction.complete()
        }
    }


    class CoroutineTransaction(
        private var completed: Boolean = false
    ) : AbstractCoroutineContextElement(CoroutineTransaction) {
        companion object Key : CoroutineContext.Key<CoroutineTransaction>

        fun complete() {
            completed = true
        }

        val incomplete: Boolean
            get() = !completed
    }


    class CoroutineTransactionTemplate(val template: TransactionTemplate) :
        AbstractCoroutineContextElement(CoroutineTransactionTemplate) {
        companion object Key : CoroutineContext.Key<CoroutineTransactionTemplate>
    }

    val CoroutineContext.transactionTemplate: TransactionTemplate
        get() = get(CoroutineTransactionTemplate)?.template
            ?: error("No transaction template in context")
}