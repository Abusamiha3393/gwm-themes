package com.sahra.bridge.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * يجد السيارات على الشبكة المحلية بثلاث طرق:
 * 1) mDNS (_havalbridge._tcp)، 2) عنوان البوابة حين يكون الجوال متصلًا بنقطة اتصال السيارة،
 * 3) مسح الشبكة /24 على المنفذ 47800 — ضروري حين يكون الجوال هو نقطة الاتصال، فبعض الأنظمة لا تمرّر mDNS.
 * كل الدوال متزامنة وتُستدعى من خيط خلفي.
 */
object Discovery {
    private const val TAG = "HavalBridge"
    private const val SERVICE_TYPE = "_havalbridge._tcp."

    fun find(context: Context, waitMs: Long = 4000, stopAtId: String? = null): List<Car> {
        val found = Collections.synchronizedMap(LinkedHashMap<String, Car>())
        val done = CountDownLatch(1)
        fun add(car: Car) {
            if (car.id.isEmpty()) return
            found[car.id] = car
            if (car.id == stopAtId) done.countDown()
        }

        val pool = Executors.newFixedThreadPool(3)
        pool.execute { mdns(context, waitMs, ::add, done) }
        pool.execute {
            gateway(context)?.let { host ->
                try {
                    add(Client.info(host, timeoutMs = 1500))
                } catch (_: Exception) {
                }
            }
        }
        pool.execute { scan(::add, done) }
        pool.shutdown()
        if (stopAtId != null) {
            done.await(waitMs + 2000, TimeUnit.MILLISECONDS)
        } else {
            pool.awaitTermination(waitMs + 3000, TimeUnit.MILLISECONDS)
        }
        pool.shutdownNow()
        return found.values.toList()
    }

    // ---- mDNS ----

    @Suppress("DEPRECATION")
    private fun mdns(context: Context, waitMs: Long, add: (Car) -> Unit, done: CountDownLatch) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("havalbridge").apply { setReferenceCounted(false) }
        // نظام Android قبل 14 يسمح بعملية resolve واحدة في كل مرة، لذا نضعها في طابور
        val toResolve = LinkedBlockingQueue<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                toResolve.offer(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        try {
            lock.acquire()
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            val end = System.currentTimeMillis() + waitMs
            while (System.currentTimeMillis() < end && done.count > 0) {
                val info = toResolve.poll(200, TimeUnit.MILLISECONDS) ?: continue
                val latch = CountDownLatch(1)
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, errorCode: Int) {
                        latch.countDown()
                    }

                    override fun onServiceResolved(s: NsdServiceInfo) {
                        val host = s.host?.hostAddress
                        if (host != null) {
                            // نتحقق عبر HTTP بدل الثقة بسجل TXT
                            try {
                                add(Client.info(host, s.port, 2000))
                            } catch (_: Exception) {
                            }
                        }
                        latch.countDown()
                    }
                })
                latch.await(3, TimeUnit.SECONDS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "mdns error", e)
        } finally {
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
            try {
                lock.release()
            } catch (_: Exception) {
            }
        }
    }

    // ---- البوابة ----

    @Suppress("DEPRECATION")
    private fun gateway(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val g = wifi.dhcpInfo?.gateway ?: return null
        if (g == 0) return null
        return "${g and 0xff}.${g shr 8 and 0xff}.${g shr 16 and 0xff}.${g shr 24 and 0xff}"
    }

    // ---- مسح الشبكة ----

    /** يمسح كل شبكة IPv4 خاصة على واجهات Wi-Fi / نقطة الاتصال، 254 عنوانًا لكل منها. */
    private fun scan(add: (Car) -> Unit, done: CountDownLatch) {
        val bases = localPrefixes()
        if (bases.isEmpty()) return
        val pool = Executors.newFixedThreadPool(48)
        for ((prefix, own) in bases) {
            for (i in 1..254) {
                val host = "$prefix.$i"
                if (host == own) continue
                pool.execute {
                    if (done.count == 0L) return@execute
                    try {
                        add(Client.info(host, timeoutMs = 500))
                    } catch (_: Exception) {
                    }
                }
            }
        }
        pool.shutdown()
        try {
            pool.awaitTermination(15, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        pool.shutdownNow()
    }

    /** أزواج (أول ثلاثة أرقام من العنوان، عنوان الجوال) لكل واجهة شبكة خاصة غير خلوية. */
    private fun localPrefixes(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") && !it.name.startsWith("ccmni") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { a -> a.hostAddress?.let { it.substringBeforeLast('.') to it } }
            .distinctBy { it.first }
            .take(3)
    } catch (e: Exception) {
        emptyList()
    }
}
