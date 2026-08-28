package com.fincore.identity

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class TestRbacController {

    @GetMapping("/customer/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun customerProfile(): Map<String, String> = mapOf("message" to "Customer profile")

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    fun adminUsers(): Map<String, String> = mapOf("message" to "Admin users")
}
