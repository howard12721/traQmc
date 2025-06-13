package com.github.howard12721.traQmc.intrastructure

import com.github.howard12721.traQmc.model.config.Config
import com.github.howard12721.traQmc.model.config.ConfigRepository
import org.bukkit.plugin.Plugin

class FileConfigRepository(private val plugin: Plugin) : ConfigRepository {

    private var config: Config? = null

    override fun load(): Config {
        plugin.reloadConfig()
        return Config(
            plugin.config.getString("token") ?: throw IllegalArgumentException("Token is required"),
            plugin.config.getString("chat_integration_channel")
                ?: throw IllegalArgumentException("Chat integration channel is required")
        ).also { config = it }
    }

    override fun get(): Config {
        return config ?: load()
    }

    override fun save(config: Config) {
        plugin.config.set("token", config.token)
        plugin.config.set("chat_integration_channel", config.chatIntegrationChannel)
        plugin.saveConfig()
        this.config = config
    }

}