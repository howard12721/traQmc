package com.github.howard12721.traQmc.model.user

import java.util.*

interface UserRepository {

    fun findByID(id: UUID): User?

    fun save(user: User)

}