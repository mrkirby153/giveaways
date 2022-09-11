package com.mrkirby153.giveaways.scheduler

/**
 * An abstract job that will be run
 */
abstract class Job<T : Any> {

    lateinit var data: T

    /**
     * Runs the job
     */
    abstract fun run()
}