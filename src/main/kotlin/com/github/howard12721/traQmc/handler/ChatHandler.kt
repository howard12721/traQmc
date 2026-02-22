package com.github.howard12721.traQmc.handler

import com.github.howard12721.traQmc.model.config.ConfigRepository
import com.github.howard12721.traQmc.model.user.UserRepository
import io.papermc.paper.event.player.AsyncChatEvent
import jp.xhw.trakt.bot.TraktClient
import jp.xhw.trakt.bot.model.ChannelHandle
import jp.xhw.trakt.bot.model.MessageCreated
import jp.xhw.trakt.bot.scope.BotScope
import jp.xhw.trakt.bot.scope.resolve
import jp.xhw.trakt.bot.scope.sendMessage
import jp.xhw.trakt.bot.scope.setTopic
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
    val traktClient: TraktClient,
) : Listener {
    context(scope: BotScope)
    suspend fun handleTraqMessage(event: MessageCreated) {
        val author = event.message.author.resolve()

        if (author.isBot) {
            return
        }

        Bukkit.getScheduler().runTask(plugin) { _ ->
            plugin.server.sendMessage(
                Component.text("<traQ:${author.name}> ${event.message.content}"),
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

            traktClient.launchAndExecute {
                ChannelHandle.of(config.chatIntegrationChannel).sendMessage(message)
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

            traktClient.launchAndExecute {
                ChannelHandle.of(config.chatIntegrationChannel).sendMessage(message)
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

            traktClient.launchAndExecute {
                ChannelHandle.of(config.chatIntegrationChannel).sendMessage(message)
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

            traktClient.launchAndExecute {
                ChannelHandle.of(config.chatIntegrationChannel).sendMessage(message)
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

            traktClient.launchAndExecute {
                ChannelHandle.of(config.chatIntegrationChannel).setTopic(stringBuilder.toString())
            }
        }
    }
}
