package idv.markkuo.cscblebridge.service.ant

import android.content.Context
import android.util.Log
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import java.lang.Math.pow
import java.lang.Math.sqrt
import java.math.BigDecimal
import kotlin.math.pow

class HRConnector(context: Context, listener: DeviceManagerListener<AntDevice.HRDevice>): AntDeviceConnector<AntPlusHeartRatePcc, AntDevice.HRDevice>(context, listener) {

    private var scanCtx: AsyncScanController<AntPlusHeartRatePcc>? = null;
    private var previousBeat : Long = 0;
    private var sPreviousBeatTime : BigDecimal = 0.toBigDecimal();
    private var rrVals  =  ArrayDeque<Double>( 10)
    override fun requestAccess(context: Context, resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc>, stateChangedReceiver: AntPluginPcc.IDeviceStateChangeReceiver, deviceNumber: Int): PccReleaseHandle<AntPlusHeartRatePcc> {
        return AntPlusHeartRatePcc.requestAccess(context, deviceNumber, 0, resultReceiver, stateChangedReceiver)
    }

    override fun startScan(context: Context, scanReceiver : AsyncScanController.IAsyncScanResultReceiver) {
        scanCtx = AntPlusHeartRatePcc.requestAsyncScanController(context, 0, scanReceiver);
    }

    override fun stopScan() {
        scanCtx?.closeScanController();
        scanCtx = null;

    }

    override fun subscribeToEvents(pcc: AntPlusHeartRatePcc) {
        pcc.subscribeHeartRateDataEvent { estTimestamp, _, computedHeartRate, heartBeatCount , sBeatTime , _ ->
            val device = getDevice(pcc)
            device.hr = computedHeartRate
            device.hrTimestamp = estTimestamp
            //Log.d( "FOOBAR", "cnt $heartBeatCount  ts: $sBeatTime  tsp: $sPreviousBeatTime $flags state $dataState")
            if ((heartBeatCount - previousBeat) > 0L) {
                //Section 5.3.6.3 https://err.no/tmp/ANT_Device_Profile_Heart_Rate_Monitor.pdf
                var rr  = (sBeatTime - sPreviousBeatTime).toDouble() / (heartBeatCount - previousBeat) * 1000.0
                rrVals.add(rr)
            }
            if (rrVals.size > 2 ) {
                // From https://downloads.hindawi.com/journals/mse/2012/931943.pdf
                // https://stackoverflow.com/questions/24624039/how-to-get-hrv-heart-rate-variability-from-hr-heart-rate
               /* var rrTotal = 0;
                for (v in rrVals) {
                    rrTotal += v
                }
                var mrr = rrTotal / rrVals.size
                var sdnnTotal : Double = 0.0;
                for (i in 0 until rrVals.size) {
                    sdnnTotal += (rrVals[i] - mrr).pow(2.0)
                }
                var sdnn = kotlin.math.sqrt(sdnnTotal / rrVals.size)
                device.hrv = sdnn*/
                var rmssdTotal : Double = 0.0;
                for (i in 1 until rrVals.size) {
                    rmssdTotal += (rrVals[i] - rrVals[i-1]).pow(2.0)
                }
                var rmssd = kotlin.math.sqrt(rmssdTotal / (rrVals.size - 1))
                // from https://help.elitehrv.com/article/54-how-do-you-calculate-the-hrv-score
                device.hrv = kotlin.math.ln(rmssd) / 6.5 * 100.0
            }
            previousBeat = heartBeatCount
            sPreviousBeatTime = sBeatTime
            listener.onDataUpdated(device)
        }
    }

    override fun init(deviceNumber: Int, deviceName: String): AntDevice.HRDevice {
        return AntDevice.HRDevice(deviceNumber, deviceName, 0, 0L, 0, 0.0)
    }
}
