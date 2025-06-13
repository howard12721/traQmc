package com.github.howard12721.traQmc.handler

import com.github.howard12721.traQmc.model.user.User
import com.github.howard12721.traQmc.model.user.UserRepository
import com.github.howard12721.trakt.rest.apis.MessageApi
import com.github.howard12721.trakt.rest.models.PostMessageRequest
import com.github.howard12721.trakt.websocket.DirectMessageCreated
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.plugin.Plugin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi

data class LinkSession(
    val id: UUID,
    val token: String,
    val expiresAt: Long
)

@OptIn(ExperimentalUuidApi::class)
class LoginHandler(
    private val plugin: Plugin,
    private val userRepository: UserRepository,
    private val messageApi: MessageApi
) : Listener {

    private val sessions = ConcurrentHashMap<UUID, LinkSession>()

    suspend fun handleDirectMessage(event: DirectMessageCreated) {
        if (event.message.text.startsWith("!verify")) {
            val args = event.message.text.split(" ")
            if (args.size != 2) {
                return
            }
            val token = args[1]
            val session = sessions.values.find { it.token == token }
            if (session == null) {
                messageApi.postMessage(
                    event.message.channelId.toString(),
                    PostMessageRequest(
                        "無効なリンクコードです。正しいコードを使用してください。",
                        false
                    )
                )
                return
            }
            val newUser = User(session.id, event.message.user.name)
            transaction {
                userRepository.save(newUser)
            }
            messageApi.postMessage(
                event.message.channelId.toString(),
                PostMessageRequest(
                    "連携が完了しました！",
                    false
                )
            )
        }
    }

    @EventHandler
    fun handleLogin(event: AsyncPlayerPreLoginEvent) {
        transaction {
            val id = event.uniqueId
            val user = userRepository.findByID(id)
            if (user == null) {
                val session = getLinkSession(id)

                val instant = Instant.ofEpochMilli(session.expiresAt)
                val zoneId = ZoneId.of("Asia/Tokyo")
                val zonedDateTime = instant.atZone(zoneId)
                val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                val formattedString = zonedDateTime.format(formatter)

                event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("このサーバーに参加するにはtraQアカウントをリンクする必要があります。")
                        .appendNewline()
                        .append(
                            Component.text("リンクするには traQで @BOT_traQmc のDMに以下のコマンドを送信してください。")
                        )
                        .appendNewline()
                        .append(
                            Component.text("「!verify ${session.token}」")
                        )
                        .appendNewline()
                        .append(Component.text("このコードの有効期限は $formattedString です"))
                )
            }
        }
    }

    private fun getLinkSession(id: UUID): LinkSession {
        return sessions[id] ?: createSession(id)
    }

    private fun createSession(id: UUID): LinkSession {
        val session = LinkSession(id, generateToken(), System.currentTimeMillis() + 15 * 60 * 1000)
        sessions[id] = session
        Bukkit.getScheduler().runTaskLaterAsynchronously(
            plugin,
            { task ->
                sessions.remove(id)
            },
            15 * 60 * 20L
        )
        return session
    }

    private fun generateToken(): String {
        val number = Random().nextInt(1000000)
        val token = number.toString().padStart(6, '0')
        return if (sessions.any { it.value.token == token }) {
            generateToken()
        } else {
            token
        }
    }

}
