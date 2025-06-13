package com.github.howard12721.traQmc

import com.github.howard12721.traQmc.handler.ChatHandler
import com.github.howard12721.traQmc.handler.LoginHandler
import com.github.howard12721.traQmc.intrastructure.FileConfigRepository
import com.github.howard12721.traQmc.intrastructure.SqlUserRepository
import com.github.howard12721.trakt.rest.apis.ChannelApi
import com.github.howard12721.trakt.rest.apis.MessageApi
import com.github.howard12721.trakt.websocket.DirectMessageCreated
import com.github.howard12721.trakt.websocket.MessageCreated
import com.github.howard12721.trakt.websocket.WebSocketClient
import org.bukkit.plugin.java.JavaPlugin

class TraQmc : JavaPlugin() {

    private val configRepository = FileConfigRepository(this)
    private val userRepository = SqlUserRepository(this)

    private lateinit var webSocketClient: WebSocketClient
    private val messageApi = MessageApi()
    private val channelApi = ChannelApi()

    override fun onEnable() {
        saveDefaultConfig()

        val config = configRepository.load()
        userRepository.connect()

        webSocketClient = WebSocketClient(config.token)
        messageApi.setBearerToken(config.token)
        channelApi.setBearerToken(config.token)

        val chatHandler = ChatHandler(this, configRepository, userRepository, messageApi, channelApi)
        server.pluginManager.registerEvents(chatHandler, this)
        webSocketClient.on<MessageCreated> {
            chatHandler.handleTraqMessage(this)
        }

        val loginHandler = LoginHandler(this, userRepository, messageApi)
        server.pluginManager.registerEvents(loginHandler, this)
        webSocketClient.on<DirectMessageCreated> {
            loginHandler.handleDirectMessage(this)
        }

        webSocketClient.start()

    }

    override fun onDisable() {
        webSocketClient.stop()
    }
}
