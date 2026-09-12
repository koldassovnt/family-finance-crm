package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "banks")
@SQLRestriction("is_deleted = false")
class Bank(
    @Column(nullable = false, length = 255)
    var name: String,
) : BaseEntity()
