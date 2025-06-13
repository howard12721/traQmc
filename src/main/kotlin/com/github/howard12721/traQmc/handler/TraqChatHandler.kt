package com.github.howard12721.traQmc.handler

import com.github.howard12721.trakt.websocket.MessageCreated
import com.github.howard12721.trakt.websocket.WebSocketClient
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

class TraqChatHandler(private val plugin: Plugin) {

    fun registerHandlers(webSocketClient: WebSocketClient) {
        webSocketClient.on<MessageCreated> {
            Bukkit.getScheduler().runTask(plugin) { _ ->
                plugin.server.sendMessage(
                    Component.text("<traQ:${message.user.name}> ${message.text}")
                )
            }
        }
    }

}