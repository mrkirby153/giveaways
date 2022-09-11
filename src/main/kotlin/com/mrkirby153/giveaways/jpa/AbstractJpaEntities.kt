package com.mrkirby153.giveaways.jpa

import org.springframework.data.util.ProxyUtils
import java.io.Serializable
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.MappedSuperclass

@MappedSuperclass
abstract class AutoIncrementingJpaEntity<T : Serializable> {
    @Id
    @GeneratedValue
    open lateinit var id: T

    override fun equals(other: Any?): Boolean {
        other ?: return false
        if (this === other) return true
        if (javaClass != ProxyUtils.getUserClass(other)) return false
        other as AutoIncrementingJpaEntity<*>
        return this.id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

@MappedSuperclass
abstract class AbstractJpaEntity<T : Serializable>(
    @Id
    val id: T
) {
    override fun equals(other: Any?): Boolean {
        other ?: return false
        if (this === other) return true
        if (javaClass != ProxyUtils.getUserClass(other)) return false
        other as AbstractJpaEntity<*>
        return this.id == other.id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}