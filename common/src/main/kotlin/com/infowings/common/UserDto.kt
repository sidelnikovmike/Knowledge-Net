package com.infowings.common

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(var username: String, var password: String)