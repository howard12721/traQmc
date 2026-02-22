package com.github.howard12721.traQmc

import com.github.howard12721.traQmc.handler.ChatHandler
import com.github.howard12721.traQmc.handler.LoginHandler
import com.github.howard12721.traQmc.intrastructure.FileConfigRepository
import com.github.howard12721.traQmc.intrastructure.SqlUserRepository
import jp.xhw.trakt.bot.TraktClient
import jp.xhw.trakt.bot.model.DirectMessageCreated
import jp.xhw.trakt.bot.model.MessageCreated
import jp.xhw.trakt.bot.trakt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

class TraQmc : JavaPlugin() {
    private val configRepository = FileConfigRepository(this)
    private val userRepository = SqlUserRepository(this)

    private val traktScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var traktClient: TraktClient

    override fun onEnable() {
        saveDefaultConfig()

        val config = configRepository.load()
        userRepository.connect()

        traktClient = trakt(config.token)

        val chatHandler = ChatHandler(this, configRepository, userRepository, traktClient)
        server.pluginManager.registerEvents(chatHandler, this)
        traktClient.on<MessageCreated> {
            chatHandler.handleTraqMessage(it)
        }

        val loginHandler = LoginHandler(this, userRepository)
        server.pluginManager.registerEvents(loginHandler, this)
        traktClient.on<DirectMessageCreated> {
            loginHandler.handleDirectMessage(it)
        }

        traktScope.launch {
            traktClient.start()
        }
    }

    override fun onDisable() {
        traktScope.launch {
            traktClient.stop()
        }
    }
}
