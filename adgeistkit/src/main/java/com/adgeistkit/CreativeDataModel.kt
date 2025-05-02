package com.adgeistkit

data class CreativeDataModel(
    val success: Boolean,
    val message: String,
    val data: Campaign?
)

data class Campaign(
    val _id: String?,
    val name: String?,
    val creative: Creative?,
    val budgetSettings: BudgetSettings?
)

data class Creative(
    val title: String?,
    val description: String?,
    val fileUrl: String?,
    val ctaUrl: String?,
    val type: String?,
    val fileName: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class BudgetSettings(
    val totalBudget: Double,
    val spentBudget: Double
)