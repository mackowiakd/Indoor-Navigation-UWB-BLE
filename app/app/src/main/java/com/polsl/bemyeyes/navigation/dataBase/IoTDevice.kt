package com.polsl.bemyeyes.navigation.dataBase

import com.google.gson.annotations.SerializedName

data class IoTDevice(
    @SerializedName("mac_address") val macAddress: String, //is universal to capture ID of uwb anchor too?
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("location_id") val locationId: Int,
    @SerializedName("semantic_role") val semanticRole: String,
    @SerializedName("tx_power_config") val txPowerConfig: Int?,
    @SerializedName("global_x") val globalX: Double?,
    @SerializedName("global_y") val globalY: Double?,
    @SerializedName("global_z") val globalZ: Double? // pietro, np 1.0 to pierwsze a 1.5 to schody na pietro drugie

)