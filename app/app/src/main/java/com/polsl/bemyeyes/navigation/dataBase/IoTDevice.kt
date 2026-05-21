package com.polsl.bemyeyes.navigation.dataBase

import com.google.gson.annotations.SerializedName

data class IoTDevice(
    @SerializedName("mac_address") val macAddress: String, //is universal to capture ID of uwb anchor too?
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("location_id") val locationId: Int,
    @SerializedName("semantic_role") val semanticRole: String,
    @SerializedName("tx_power_config") val txPowerConfig: Int?
)