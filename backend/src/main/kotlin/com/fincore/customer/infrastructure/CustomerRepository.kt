package com.fincore.customer.infrastructure

import com.fincore.customer.domain.Customer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CustomerRepository : JpaRepository<Customer, UUID> {
    fun findByEmail(email: String): Optional<Customer>
}
