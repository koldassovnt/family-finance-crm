package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.proxy.HibernateProxy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Every entity in this system is soft-deleted and audited — see
 * `00-architecture-and-foundations.md`. Entities are `open class` with `var`
 * fields and ID-based equality that tolerates an unsaved (null) id.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(nullable = false)
    var isDeleted: Boolean = false

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant? = null

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val thisClass = unproxiedClass(this)
        if (thisClass != unproxiedClass(other)) return false
        val thisId = id ?: return false
        return thisId == (other as BaseEntity).id
    }

    final override fun hashCode(): Int = unproxiedClass(this).hashCode()

    private fun unproxiedClass(entity: Any): Class<*> =
        if (entity is HibernateProxy) {
            entity.hibernateLazyInitializer.persistentClass
        } else {
            entity.javaClass
        }
}
