package com.github.howard12721.traQmc.handler

import com.github.howard12721.traQmc.model.config.ConfigRepository
import com.github.howard12721.traQmc.model.user.UserRepository
import com.github.howard12721.trakt.rest.apis.ChannelApi
import com.github.howard12721.trakt.rest.apis.MessageApi
import com.github.howard12721.trakt.rest.models.PostMessageRequest
import com.github.howard12721.trakt.rest.models.PutChannelTopicRequest
import com.github.howard12721.trakt.websocket.MessageCreated
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ChatHandler(
    val plugin: Plugin,
    val configRepository: ConfigRepository,
    val userRepository: UserRepository,
    val messageApi: MessageApi,
    val channelApi: ChannelApi,
) : Listener {

    fun handleTraqMessage(event: MessageCreated) {
        Bukkit.getScheduler().runTask(plugin) { _ ->
            plugin.server.sendMessage(
                Component.text("<traQ:${event.message.user.name}> ${event.message.text}")
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun handleMinecraftMessage(event: AsyncChatEvent) {
        transaction {
            val component = event.message()
            if (component !is TextComponent) {
                return@transaction
            }

            val config = configRepository.get()
            if (config.chatIntegrationChannel.isBlank()) {
                return@transaction
            }
            val user = userRepository.findByID(event.player.uniqueId) ?: return@transaction

            val name = event.player.name
            val message = ":@${user.traqId}:<$name> ${component.content()}"

            CoroutineScope(Dispatchers.IO).launch {
                messageApi.postMessage(
                    config.chatIntegrationChannel,
                    PostMessageRequest(message)
                )
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, { event ->
            updateTopics()
        }, 60L)
        transaction {
            val config = configRepository.get()
            if (config.chatIntegrationChannel.isBlank()) {
                return@transaction
            }
            val user = userRepository.findByID(event.player.uniqueId) ?: return@transaction

            val name = event.player.name
            val message = ":@${user.traqId}:$name がサバイバルに参加しました。"

            CoroutineScope(Dispatchers.IO).launch {
                messageApi.postMessage(
                    config.chatIntegrationChannel,
                    PostMessageRequest(message)
                )
            }
        }
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, { event ->
            updateTopics()
        }, 60L)
        transaction {
            val config = configRepository.get()
            if (config.chatIntegrationChannel.isBlank()) {
                return@transaction
            }
            val user = userRepository.findByID(event.player.uniqueId) ?: return@transaction

            val name = event.player.name
            val message = ":@${user.traqId}:$name がサバイバルから退出しました。"

            CoroutineScope(Dispatchers.IO).launch {
                messageApi.postMessage(
                    config.chatIntegrationChannel,
                    PostMessageRequest(message)
                )
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val deathMessageComponent = event.deathMessage() ?: return
        val deathMessage = PlainTextComponentSerializer.plainText().serialize(deathMessageComponent)
        transaction {
            val config = configRepository.get()
            if (config.chatIntegrationChannel.isBlank()) {
                return@transaction
            }
            val user = userRepository.findByID(event.player.uniqueId) ?: return@transaction

            val message = ":@${user.traqId}:" + deathMessage

            CoroutineScope(Dispatchers.IO).launch {
                messageApi.postMessage(
                    config.chatIntegrationChannel,
                    PostMessageRequest(message)
                )
            }
        }
    }

    fun updateTopics() {
        val config = configRepository.get()
        if (config.chatIntegrationChannel.isBlank()) {
            return
        }

        transaction {
            val players = Bukkit.getOnlinePlayers()
            val stringBuilder = StringBuilder("現在の参加者数: ${players.size}人 ")
            for (player in Bukkit.getOnlinePlayers()) {
                val user = userRepository.findByID(player.uniqueId) ?: continue
                stringBuilder.append(":@${user.traqId}:")
            }
            CoroutineScope(Dispatchers.IO).launch {
                channelApi.editChannelTopic(
                    config.chatIntegrationChannel,
                    PutChannelTopicRequest(
                        stringBuilder.toString(),
                    )
                )
            }
        }
    }

}
