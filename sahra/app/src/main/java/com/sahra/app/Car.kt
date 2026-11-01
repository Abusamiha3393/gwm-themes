package com.sahra.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import kotlin.math.sin

/**
 * مفاتيح خدمة السيارة gwm_adapter.
 * LIVE: المفاتيح التي ثبت عملها في هافال H9 وتُقرأ باستمرار.
 * PROBE: مفاتيح مرشحة نفحصها في شاشة «بيانات السيارة» لنعرف ما تدعمه السيارة قبل بناء أي ميزة عليها.
 */
object CarKeys {
    const val SPEED = "car.basic.ivivehiclespeed"
    const val SPEED2 = "car.basic.vehicle_speed"
    const val RPM = "car.basic.engine_speed"
    const val GEAR = "car.basic.current_gear"
    const val COOLANT = "car.basic.coolant_temp"
    const val FUEL = "car.basic.remain_fuel_percentage"
    const val RANGE = "car.basic.remain_odometer"
    const val ODO = "car.basic.total_odometer"
    const val ODO2 = "car.basic.odometer"
    const val BATTERY = "car.basic.battery_voltage"
    const val OUTSIDE = "car.basic.outside_temp"
    const val INSIDE = "car.basic.inside_temp"
    const val STEERING = "car.basic.steering_wheel_angle"
    const val PITCH = "car.off_road_info.pitch_angle"
    const val ROLL = "car.off_road_info.roll_angle"
    const val COMPASS = "car.off_road_info.compass_direction"
    const val ALTITUDE = "car.off_road_info.altitude"
    const val DRIVE_MODE = "car.off_road_info.drive_mode"

    val LIVE = arrayOf(SPEED, SPEED2, RPM, GEAR, COOLANT, FUEL, RANGE, ODO, ODO2, BATTERY, OUTSIDE, INSIDE,
        STEERING, PITCH, ROLL, COMPASS, ALTITUDE, DRIVE_MODE)

    /** مجموعات للفحص، بأسماء عربية تشرح الغرض. */
    val PROBE: List<Pair<String, List<String>>> = listOf(
        "أساسية" to listOf(
            "car.basic.vin", SPEED, SPEED2, RPM, GEAR, "car.basic.gear_status", COOLANT, FUEL, RANGE,
            ODO, ODO2, "car.basic.accumulated_odometer", "car.basic.cur_journey_odometer", "car.basic.trip_odometer",
            BATTERY, "car.basic.battery_power_level", OUTSIDE, INSIDE, STEERING,
        ),
        "الوقود والرحلة" to listOf(
            "car.basic.instant_fuel_consumption", "car.basic.average_fuel_consumption", "car.basic.avg_fuel_consumption",
            "car.basic.cur_journey_avg_fuel", "car.basic.cur_journey_time", "car.basic.average_speed",
            "car.basic.master.oil.tank.level", "car.basic.engne_oil_level_notify",
        ),
        "الإطارات" to listOf(
            "car.basic.tpms_status", "car.off_road_info.tire_pressure_fl", "car.off_road_info.tire_pressure_fr",
            "car.off_road_info.tire_pressure_rl", "car.off_road_info.tire_pressure_rr",
            "car.basic.tire_pressure_fl", "car.basic.tire_pressure_fr", "car.basic.tire_pressure_rl", "car.basic.tire_pressure_rr",
            "car.basic.tire_temp_fl", "car.basic.tire_temp_fr", "car.basic.tire_temp_rl", "car.basic.tire_temp_rr",
        ),
        "الطرق الوعرة" to listOf(
            PITCH, ROLL, COMPASS, ALTITUDE, "car.basic.baropressure", "car.basic.altitude.status", DRIVE_MODE,
            "car.off_road_info.four_low_mode_state", "car.off_road_info.driving_mod_man_shift",
            "car.off_road_info.front.edl.state", "car.off_road_info.rear.edl.state", "car.off_road_info.middle.edl.state",
            "car.off_road_info.susp_lvl_adj_state", "car.off_road_info.cur_lvl_mode", "car.off_road_info.tod.systemoper_mode",
            "car.off_road_info.expert_mode.sw_state", "car.off_road_info.cco.status", "car.off_road_setting.coo_enable",
            "car.off_road_info.engine_torque", "car.off_road_info.engine_flywheel_torque", "car.off_road_info.expected_torque",
            "car.off_road_info.engine_rpm",
        ),
        "النظام" to listOf("sys.other.keyevent_notify"),
    )
}

/** حالة السيارة. القيم غير المعروفة NaN. */
data class CarState(
    val speed: Float = 0f,
    val rpm: Float = 0f,
    val gear: String = "",
    val gearFromCar: Boolean = false,
    val fuel: Float = Float.NaN,
    val range: Float = Float.NaN,
    val odometer: Float = Float.NaN,
    val coolant: Float = Float.NaN,
    val battery: Float = Float.NaN,
    val outside: Float = Float.NaN,
    val inside: Float = Float.NaN,
    val steering: Float = Float.NaN,
    val pitch: Float = Float.NaN,
    val roll: Float = Float.NaN,
    val compass: Float = Float.NaN,
    val altitude: Float = Float.NaN,
    val driveMode: String = "",
    val live: Boolean = false,
    val demo: Boolean = false,
    val at: Long = System.currentTimeMillis(),
)

/**
 * يقرأ بيانات السيارة من gwm_adapter مرتين في الثانية ويوزعها على المستمعين في الخيط الرئيسي.
 * بدون الخدمة (هاتف أو محاكي) أو في الوضع التجريبي: بيانات متحركة وهمية.
 */
object Car {
    private const val TAG = "Car"

    fun interface Listener { fun onCar(s: CarState) }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var state = CarState(); private set
    @Volatile var available: Boolean? = null; private set
    @Volatile private var running = false
    private var thread: Thread? = null
    private var app: Context? = null

    fun listen(l: Listener) { listeners.add(l); l.onCar(state) }
    fun unlisten(l: Listener) { listeners.remove(l) }

    @Synchronized
    fun start(context: Context) {
        app = context.applicationContext
        if (running) return
        running = true
        thread = Thread(::loop, "car").apply { isDaemon = true; start() }
    }

    private fun loop() {
        var t = 0.0
        while (running) {
            try {
                val ctx = app ?: return
                val prefs = Prefs(ctx)
                val values = if (prefs.demo) null else GwmAdapter.readKeys(ctx.packageName, CarKeys.LIVE)
                available = values != null
                val s = if (values != null) parse(values, prefs.speedFactor) else { t += 0.15; demo(t) }
                state = s
                main.post { listeners.forEach { it.onCar(s) } }
                Thread.sleep(if (values != null) 500 else 150)
            } catch (_: InterruptedException) {
                return
            } catch (e: Throwable) {
                Log.e(TAG, "loop: ${e.message}")
                try { Thread.sleep(1000) } catch (_: InterruptedException) { return }
            }
        }
    }

    /** يقرأ مفاتيح محددة مرة واحدة (لشاشة الفحص). يعمل في خيط خلفي فقط. */
    fun probe(context: Context, keys: List<String>): Map<String, String?> {
        val out = LinkedHashMap<String, String?>()
        keys.forEach { k -> out[k] = GwmAdapter.readKeys(context.packageName, arrayOf(k))?.firstOrNull() }
        return out
    }

    private fun clean(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return raw.trim().removePrefix("[").removeSuffix("]").trim()
    }

    private fun parse(v: Array<String?>, speedFactor: Float): CarState {
        val m = HashMap<String, String>()
        CarKeys.LIVE.forEachIndexed { i, k -> clean(v.getOrNull(i))?.let { m[k] = it } }
        fun f(k: String) = m[k]?.toFloatOrNull() ?: Float.NaN
        var speed = f(CarKeys.SPEED).let { if (it.isNaN() || it == 0f) f(CarKeys.SPEED2) else it }
        if (speed.isNaN()) speed = 0f
        val rpm = f(CarKeys.RPM).let { if (it.isNaN()) 0f else max(0f, it) }
        val rawGear = m[CarKeys.GEAR]
        val gear = gearName(rawGear)
        return CarState(
            speed = max(0f, speed) * speedFactor,
            rpm = rpm,
            gear = gear ?: guessGear(speed, rpm),
            gearFromCar = gear != null,
            fuel = f(CarKeys.FUEL).coerceIn(0f, 100f),
            range = f(CarKeys.RANGE),
            odometer = f(CarKeys.ODO).let { if (it.isNaN() || it <= 0f) f(CarKeys.ODO2) else it },
            coolant = f(CarKeys.COOLANT),
            battery = f(CarKeys.BATTERY),
            outside = f(CarKeys.OUTSIDE),
            inside = f(CarKeys.INSIDE),
            steering = f(CarKeys.STEERING),
            pitch = f(CarKeys.PITCH),
            roll = f(CarKeys.ROLL),
            compass = f(CarKeys.COMPASS),
            altitude = f(CarKeys.ALTITUDE),
            driveMode = m[CarKeys.DRIVE_MODE].orEmpty(),
            live = true,
        )
    }

    /** قيمة الغيار من السيارة إن كانت حرفًا معروفًا، وإلا null. الأرقام نعرف معناها بعد فحص سيارتك. */
    private fun gearName(raw: String?): String? {
        val r = raw?.uppercase()?.trim() ?: return null
        return when {
            r in setOf("P", "R", "N", "D", "S", "M") -> r
            r.startsWith("D") || r.startsWith("M") -> r
            else -> null
        }
    }

    private fun guessGear(speed: Float, rpm: Float) = when {
        rpm < 100f && speed < 1f -> "P"
        speed < 1f -> "N"
        else -> "D"
    }

    private fun demo(t: Double): CarState {
        val c = (sin(t / 6.0) + 1.0) / 2.0
        val speed = (c * 140).toFloat()
        return CarState(
            speed = speed, rpm = (900 + c * 3800 + 300 * sin(t * 1.7)).toFloat(),
            gear = if (speed < 1f) "N" else "D", fuel = 62f, range = 438f, odometer = 23518f,
            coolant = 88f, battery = 14.1f, outside = 34f, inside = 23f,
            steering = (25 * sin(t / 2.0)).toFloat(), pitch = (12 * sin(t / 3.1)).toFloat(),
            roll = (8 * sin(t / 2.3)).toFloat(), compass = ((t * 6.0) % 360.0).toFloat(),
            altitude = (612 + 40 * sin(t / 9.0)).toFloat(), demo = true,
        )
    }
}
