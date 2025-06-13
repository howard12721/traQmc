package com.github.howard12721.traQmc.intrastructure

import com.github.howard12721.traQmc.model.user.User
import com.github.howard12721.traQmc.model.user.UserRepository
import org.bukkit.plugin.Plugin
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*

data class DatabaseInfo(val url: String, val user: String, val password: String, val database: String)

object Users : UUIDTable("traqmc_users") {
    val traqId: Column<String> = text("traqId")
}

class UserDao(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserDao>(Users)

    var traqId by Users.traqId
}

class SqlUserRepository(private val plugin: Plugin) : UserRepository {

    fun connect() {
        plugin.reloadConfig()
        val databaseInfo = DatabaseInfo(
            plugin.config.getString("database.url") ?: throw IllegalArgumentException("Database url is required"),
            plugin.config.getString("database.user") ?: throw IllegalArgumentException("Database user is required"),
            plugin.config.getString("database.password")
                ?: throw IllegalArgumentException("Database password is required"),
            plugin.config.getString("database.database")
                ?: throw IllegalArgumentException("Database name is required")
        )
        databaseInfo.let {
            Database.connect(
                url = "jdbc:mariadb://${it.url}/${it.database}",
                driver = "org.mariadb.jdbc.Driver",
                user = it.user,
                password = it.password,
            )
        }

        transaction {
            SchemaUtils.create(Users)
        }
    }

    override fun findByID(id: UUID): User? {
        val user = UserDao.findById(id) ?: return null
        return User(
            id = user.id.value,
            traqId = user.traqId
        )
    }

    override fun save(user: User) {
        val existingUser = UserDao.findById(user.id)
        if (existingUser != null) {
            existingUser.traqId = user.traqId
        } else {
            UserDao.new(user.id) {
                traqId = user.traqId
            }
        }
    }

}