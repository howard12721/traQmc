package com.github.howard12721.traQmc.model.config

interface ConfigRepository {

    fun load(): Config

    fun get(): Config

    fun save(config: Config)

}