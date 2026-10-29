package com.sahra.dash

import org.json.JSONObject

/** حالة السيارة كما تُرسل إلى الثيمات. القيم غير المعروفة تبقى NaN وتُرسل null. */
data class VehicleState(
    val speed: Float = 0f,
    val rpm: Float = 0f,
    val gear: String = "P",
    val fuelPercent: Float = Float.NaN,
    val rangeKm: Float = Float.NaN,
    val odometerKm: Float = Float.NaN,
    val coolantTemp: Float = Float.NaN,
    val outsideTemp: Float = Float.NaN,
    val insideTemp: Float = Float.NaN,
    val batteryVoltage: Float = Float.NaN,
    val batteryLevel: Float = Float.NaN,
    val steeringAngle: Float = Float.NaN,
    val pitch: Float = Float.NaN,
    val roll: Float = Float.NaN,
    val compass: Float = Float.NaN,
    val altitude: Float = Float.NaN,
    val vin: String = "",
    val live: Boolean = false,
    val demo: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("speed", speed.toDouble())
        put("rpm", rpm.toDouble())
        put("gear", gear)
        putNum("fuel", fuelPercent)
        putNum("range", rangeKm)
        putNum("odometer", odometerKm)
        putNum("coolant", coolantTemp)
        putNum("outsideTemp", outsideTemp)
        putNum("insideTemp", insideTemp)
        putNum("batteryVoltage", batteryVoltage)
        putNum("batteryLevel", batteryLevel)
        putNum("steering", steeringAngle)
        putNum("pitch", pitch)
        putNum("roll", roll)
        putNum("compass", compass)
        putNum("altitude", altitude)
        put("vin", vin)
        put("live", live)
        put("demo", demo)
    }

    private fun JSONObject.putNum(name: String, v: Float) {
        put(name, if (v.isNaN()) JSONObject.NULL else v.toDouble())
    }
}
