package com.mrkirby153.giveaways.jpa

import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.springframework.data.util.ProxyUtils
import java.io.Serializable

@MappedSuperclass
abstract class AutoIncrementingJpaEntity<T : Serializable> {
    @Id
    @GeneratedValue
    @GenericGenerator(
        name = "sequence-generator",
        strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
        parameters = [Parameter(
            name = "initial_value",
            value = "1"
        ), Parameter(name = "increment_size", value = "1")]
    )
    open var id: T? = null

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