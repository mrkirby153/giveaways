package com.mrkirby153.giveaways.config

import com.mrkirby153.giveaways.utils.log
import org.quartz.spi.TriggerFiredBundle
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SpringBeanJobFactory

@Configuration
class SpringQuartzScheduler(
    private val context: ApplicationContext
) {

    @Bean
    fun springBeanJobFactory(): SpringBeanJobFactory {
        val factory = AutowiringSpringBeanJobFactory()
        log.debug("Configuring job factory")
        factory.setApplicationContext(context)
        return factory
    }
}

class AutowiringSpringBeanJobFactory : SpringBeanJobFactory(), ApplicationContextAware {

    @Transient
    private lateinit var beanFactory: AutowireCapableBeanFactory

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.beanFactory = applicationContext.autowireCapableBeanFactory
    }

    override fun createJobInstance(bundle: TriggerFiredBundle): Any {
        val job = super.createJobInstance(bundle)
        beanFactory.autowireBean(job)
        return job
    }
}