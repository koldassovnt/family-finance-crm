package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(name = "users")
@SQLRestriction("is_deleted = false")
class User(
    @Column(nullable = false, length = 255)
    var email: String,
    @Column(nullable = false, length = 255)
    var displayName: String,
    @Column(nullable = false)
    var passwordHash: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var role: UserRole,
    /**
     * Tokens issued before this instant are rejected. With no server-side token
     * store, this is the only way to cut short a token that has leaked —
     * changing your password logs every other session out.
     */
    @Column(nullable = false)
    var passwordChangedAt: Instant,
) : BaseEntity()
