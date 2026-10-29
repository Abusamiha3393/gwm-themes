package com.sahra.dash

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import kotlin.math.sin

/**
 * يقرأ بيانات السيارة من خدمة GWM كل ثانية، وأزرار المقود كل 150 ملي ثانية.
 * إذا لم تتوفر الخدمة (هاتف أو محاكي) أو فُعّل الوضع التجريبي، يولّد بيانات وهمية متحركة.
 */
object VehicleRepository {
    private const val TAG = "VehicleRepo"

    interface Listener {
        fun onState(state: VehicleState)
        fun onSteeringKey(action: SteeringAction) {}
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var state = VehicleState()
        private set

    @Volatile var forceDemo = false
    @Volatile private var running = false
    private var dataThread: Thread? = null
    private var keyThread: Thread? = null
    private lateinit var pkg: String

    private val KEYS = arrayOf(
        "car.basic.vin",
        "car.basic.ivivehiclespeed",
        "car.basic.vehicle_speed",
        "car.basic.engine_speed",
        "car.basic.coolant_temp",
        "car.basic.steering_wheel_angle",
        "car.basic.remain_fuel_percentage",
        "car.basic.remain_odometer",
        "car.basic.total_odometer",
        "car.basic.odometer",
        "car.basic.battery_voltage",
        "car.basic.battery_power_level",
        "car.basic.outside_temp",
        "car.basic.inside_temp",
        "car.off_road_info.pitch_angle",
        "car.off_road_info.roll_angle",
        "car.off_road_info.compass_direction",
        "car.off_road_info.altitude",
    )

    fun addListener(l: Listener) { listeners.add(l); l.onState(state) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    @Synchronized
    fun start(packageName: String) {
        if (running) return
        pkg = packageName
        running = true
        dataThread = Thread(::dataLoop, "vehicle-data").apply { isDaemon = true; start() }
        keyThread = Thread(::keyLoop, "vehicle-keys").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        running = false
        dataThread?.interrupt(); keyThread?.interrupt()
        dataThread = null; keyThread = null
    }

    // ---------------- البيانات ----------------

    private fun dataLoop() {
        var t = 0.0
        while (running) {
            try {
                val values = if (forceDemo) null else GwmAdapter.readKeys(pkg, KEYS)
                if (values != null) {
                    var s = state.copy(live = true, demo = false)
                    KEYS.forEachIndexed { i, key ->
                        val raw = values.getOrNull(i)
                        if (!raw.isNullOrBlank() && raw != "null") s = apply(s, key, clean(raw))
                    }
                    s = s.copy(gear = gearFor(s))
                    publish(s)
                    Thread.sleep(1000)
                } else {
                    // وضع تجريبي: تحديث سلس 10 مرات في الثانية
                    t += 0.1
                    publish(demoState(t))
                    Thread.sleep(100)
                }
            } catch (_: InterruptedException) {
                return
            } catch (e: Throwable) {
                Log.e(TAG, "data loop: ${e.message}")
                try { Thread.sleep(1000) } catch (_: InterruptedException) { return }
            }
        }
    }

    private fun clean(raw: String) = raw.trim().removePrefix("[").removeSuffix("]").trim()

    private fun apply(s: VehicleState, key: String, v: String): VehicleState {
        if (key == "car.basic.vin") return if (v.length >= 11) s.copy(vin = v) else s
        val f = v.toFloatOrNull() ?: return s
        return when (key) {
            "car.basic.ivivehiclespeed" -> s.copy(speed = max(0f, f))
            "car.basic.vehicle_speed" -> if (s.speed == 0f) s.copy(speed = max(0f, f)) else s
            "car.basic.engine_speed" -> s.copy(rpm = max(0f, f))
            "car.basic.coolant_temp" -> s.copy(coolantTemp = f)
            "car.basic.steering_wheel_angle" -> s.copy(steeringAngle = f)
            "car.basic.remain_fuel_percentage" -> s.copy(fuelPercent = f.coerceIn(0f, 100f))
            "car.basic.remain_odometer" -> s.copy(rangeKm = f)
            "car.basic.total_odometer", "car.basic.odometer" -> if (f > 0f) s.copy(odometerKm = f) else s
            "car.basic.battery_voltage" -> s.copy(batteryVoltage = f)
            "car.basic.battery_power_level" -> s.copy(batteryLevel = f)
            "car.basic.outside_temp" -> s.copy(outsideTemp = f)
            "car.basic.inside_temp" -> s.copy(insideTemp = f)
            "car.off_road_info.pitch_angle" -> s.copy(pitch = f)
            "car.off_road_info.roll_angle" -> s.copy(roll = f)
            "car.off_road_info.compass_direction" -> s.copy(compass = f)
            "car.off_road_info.altitude" -> s.copy(altitude = f)
            else -> s
        }
    }

    /** الخدمة لا تعطي الغيار بشكل موثوق، فنستنتجه من السرعة والدورات. */
    private fun gearFor(s: VehicleState) = when {
        s.rpm < 100f && s.speed < 1f -> "P"
        s.speed < 1f -> "N"
        else -> "D"
    }

    private fun demoState(t: Double): VehicleState {
        val cycle = (sin(t / 6.0) + 1.0) / 2.0          // 0..1 تسارع وتباطؤ
        val speed = (cycle * 140.0).toFloat()
        val rpm = (900 + cycle * 3800 + 300 * sin(t * 1.7)).toFloat()
        return VehicleState(
            speed = speed,
            rpm = rpm,
            gear = if (speed < 1f) "N" else "D",
            fuelPercent = 62f,
            rangeKm = 438f,
            odometerKm = 23518f,
            coolantTemp = 88f,
            outsideTemp = 34f,
            insideTemp = 23f,
            batteryVoltage = 14.1f,
            batteryLevel = 86f,
            steeringAngle = (25 * sin(t / 2.0)).toFloat(),
            pitch = (12 * sin(t / 3.1)).toFloat(),
            roll = (8 * sin(t / 2.3)).toFloat(),
            compass = (((t * 6.0) % 360.0)).toFloat(),
            altitude = (612 + 40 * sin(t / 9.0)).toFloat(),
            vin = "",
            live = false,
            demo = true,
        )
    }

    private fun publish(s: VehicleState) {
        state = s
        main.post { listeners.forEach { it.onState(s) } }
    }

    // ---------------- أزرار المقود ----------------

    private fun keyLoop() {
        var last = ""
        while (running) {
            try {
                if (!forceDemo) {
                    val raw = GwmAdapter.readKeys(pkg, arrayOf("sys.other.keyevent_notify"))?.firstOrNull()
                    if (!raw.isNullOrBlank() && raw != "null" && raw != last) {
                        last = raw
                        SteeringAction.fromRaw(raw)?.let { a -> main.post { listeners.forEach { it.onSteeringKey(a) } } }
                    }
                }
                Thread.sleep(if (GwmAdapter.isAvailable()) 150 else 2000)
            } catch (_: InterruptedException) {
                return
            } catch (e: Throwable) {
                Log.e(TAG, "key loop: ${e.message}")
            }
        }
    }
}

/** أزرار المقود المعروفة (الرموز 131 إلى 137 في شاشات GWM). */
enum class SteeringAction(val code: Int) {
    K1(131), K2(132), K3(133), K4(134), K5(135), K6(136), K7(137);

    companion object {
        private val re = Regex("""\[?(\d+)""")
        fun fromRaw(raw: String): SteeringAction? {
            val code = re.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            return entries.firstOrNull { it.code == code }
        }
    }
}
