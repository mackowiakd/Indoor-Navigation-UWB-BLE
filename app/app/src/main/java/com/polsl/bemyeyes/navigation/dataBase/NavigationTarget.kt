package com.polsl.bemyeyes.navigation.dataBase



import com.google.gson.annotations.SerializedName

data class NavigationTarget(
    @SerializedName("target_id") val targetId: Int,
    @SerializedName("location_id") val locationId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String,
    @SerializedName("associated_mac") val associatedMac: String?,
    @SerializedName("is_macro_target") val isMacroTarget: Boolean
)