package com.fincore.customer.application

import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CustomerService(
    private val customerRepository: CustomerRepository
) {
    @Transactional
    fun createCustomer(email: String, fullName: String): Customer {
        return customerRepository.save(
            Customer(
                email = email,
                fullName = fullName,
                status = CustomerStatus.ACTIVE
            )
        )
    }
}
