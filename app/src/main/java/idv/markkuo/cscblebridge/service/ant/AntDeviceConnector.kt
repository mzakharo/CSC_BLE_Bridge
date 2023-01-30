package idv.markkuo.cscblebridge.service.ant

import android.content.Context
import android.util.Log
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IDeviceStateChangeReceiver
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import java.util.concurrent.ConcurrentHashMap

abstract class AntDeviceConnector<T: AntPluginPcc, Data: AntDevice>(private val context: Context, internal val listener: DeviceManagerListener<Data>) {

    interface DeviceManagerListener<Data> {
        fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState)
        fun onDataUpdated(data: Data)
        fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int)
    }

    companion object {
        private const val TAG = "AntDeviceManager"
    }


    private var releaseHandle: PccReleaseHandle<T>? = null
    private var deviceStateChangedReceiver: IDeviceStateChangeReceiver = IDeviceStateChangeReceiver {
        Log.d(TAG, "Device State Changed ${it.name}")
        listener.onDeviceStateChanged(RequestAccessResult.SUCCESS, it);
    }
    private val resultReceiver = AntPluginPcc.IPluginAccessResultReceiver {
        pcc: T?, requestAccessResult: RequestAccessResult, deviceState: DeviceState ->
        when (requestAccessResult) {
            RequestAccessResult.SUCCESS -> {
                if (pcc != null) {
                    Log.d(TAG, "${pcc.deviceName}: ${deviceState})")
                    subscribeToEvents(pcc)
                }
            }
            RequestAccessResult.USER_CANCELLED -> {
                Log.d(TAG, "Ant Device Closed: $requestAccessResult")
            }
            else -> {
                Log.w(TAG, "Ant Device State changed: $deviceState, resultCode: $requestAccessResult")
            }
        }
        listener.onDeviceStateChanged(requestAccessResult, deviceState)
    }

    abstract fun requestAccess(
            context: Context,
            resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<T>,
            stateChangedReceiver: IDeviceStateChangeReceiver,
            deviceNumber: Int
    ): PccReleaseHandle<T>

    abstract fun startScan(context: Context, scanReceiver : AsyncScanController.IAsyncScanResultReceiver)
    abstract fun stopScan()
    abstract fun subscribeToEvents(pcc: T)
    abstract fun init(deviceNumber: Int, deviceName: String): Data


    private val scanReceiver: AsyncScanController.IAsyncScanResultReceiver = object : AsyncScanController.IAsyncScanResultReceiver {
        override fun onSearchResult(arg0: AsyncScanController.AsyncScanResultDeviceInfo) {
            Log.i(TAG, "onSearchResult ${arg0.antDeviceNumber} ${arg0.deviceDisplayName}")
            releaseHandle = requestAccess(context, resultReceiver, deviceStateChangedReceiver, arg0.antDeviceNumber);
           /* if (hrClient == null) return
            if (hrClientHandler == null) return
            val ref: HRDeviceRef = HRDeviceRef.create(NAME, arg0.getDeviceDisplayName(),
                    Integer.toString(arg0.getAntDeviceNumber()))
            if ((mIsConnecting || mIsConnected) &&
                    ref.deviceAddress.equals(connectRef.deviceAddress) &&
                    ref.deviceName.equals(connectRef.deviceName)) {
                stopScan()
                releaseHandle = AntPlusHeartRatePcc.requestAccess(context,
                        arg0.getAntDeviceNumber(), 0,
                        resultReceiver, stateReceiver)
                return
            }
            if (mScanDevices.contains(ref.deviceAddress)) return
            mScanDevices.add(ref.deviceAddress)
            hrClientHandler.post {
                if (mIsScanning) { // NOTE: mIsScanning in user-thread
                    hrClient.onScanResult(HRDeviceRef.create(NAME, arg0.getDeviceDisplayName(),
                            Integer.toString(arg0.getAntDeviceNumber())))
                }
            }*/
        }
        override fun onSearchStopped(arg0: RequestAccessResult) {
            Log.i(TAG, "onSearchStopped($arg0)")
        }
    }

    fun startSearch() {
        stopSearch()
        //releaseHandle = requestAccess(context, resultReceiver, deviceStateChangedReceiver, 0)
        startScan(context, scanReceiver)
    }

    fun stopSearch() {
        stopScan();
        releaseHandle?.close()
        releaseHandle = null;
    }

    internal fun getDevice(pcc: T): Data {
        return init(pcc.antDeviceNumber, pcc.deviceName);
    }
}