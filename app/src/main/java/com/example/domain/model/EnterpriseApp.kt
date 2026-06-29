package com.example.domain.model

data class EnterpriseApp(
    val id: String,
    val name: String,
    val packageName: String,
    val version: String,
    val status: String, // "NOT_INSTALLED", "PENDING_UPDATE", "INSTALLING", "INSTALLED"
    val progress: Float = 0f,
    val sizeMb: Int = 15
)
