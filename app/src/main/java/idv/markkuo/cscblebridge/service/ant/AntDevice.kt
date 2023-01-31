package idv.markkuo.cscblebridge.service.ant

//import java.time.Instant
//import java.time.LocalDate
import android.os.SystemClock
import idv.markkuo.cscblebridge.service.ble.BleServiceType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

sealed class AntDevice(val deviceId: Int, val deviceName: String, val typeName: String, val bleType: BleServiceType) {
    data class BsdDevice(
            private val id: Int,
            private val name: String,
            var lastSpeed: Float,
            var cumulativeWheelRevolution: Long,
            var lastWheelEventTime: Int,
            var lastSpeedTimestamp: Long
    ): AntDevice(id, name, "ANT+ Bike Speed", BleServiceType.CscService) {
        override fun getDataString(): String {
            return "Speed: $lastSpeed, RPM: $cumulativeWheelRevolution"
        }
    }

    data class BcDevice(
            private val id: Int,
            private val name: String,
            var cadence: Int,
            var cumulativeCrankRevolution: Long,
            var crankEventTime: Long,
            var cadenceTimestamp: Long
    ) : AntDevice(id, name, "ANT+ Bike Cadence", BleServiceType.CscService) {
        override fun getDataString(): String {
            return "Cadence: $cadence, Crank Revolution: $cumulativeCrankRevolution"
        }
    }

    data class SSDevice(
            private val id: Int,
            private val name: String,
            var ssDistance: Long,
            var ssDistanceTimestamp: Long,
            var ssSpeed: Float,
            var ssSpeedTimestamp: Long,
            var stridePerMinute: Long,
            var stridePerMinuteTimestamp: Long
    ) : AntDevice(id, name, "ANT+ Stride SDM", BleServiceType.RscService) {
        override fun getDataString(): String {
            return "Speed: $ssSpeed, Stride/Min: $stridePerMinute"
        }
    }

    data class HRDevice(
            private val id: Int,
            private val name: String,
            var hr: Int,
            var hrTimestamp: Long,
            var rr: Int,
            var hrv : Double,
    ) : AntDevice(id, name, "ANT+ Heart Rate. ID: $id", BleServiceType.HrService) {
       override fun getDataString(): String {
            val ts =  Date(System.currentTimeMillis() - SystemClock.elapsedRealtime() + hrTimestamp);
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            return "HR: $hr. HRV: %.2f ts: ${sdf.format(ts)}".format(hrv)
        }
    }

    abstract fun getDataString(): String
}