package idv.markkuo.cscblebridge.service

import android.content.Context
import android.util.Log
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import idv.markkuo.cscblebridge.service.ant.*
import idv.markkuo.cscblebridge.service.ble.BleServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.ArrayList

class AntToBleBridge {

    private val antConnectors = ArrayList<AntDeviceConnector<*, *>>()

    val antDevices = hashMapOf<Int, AntDevice>()
    val selectedDevices = hashMapOf<BleServiceType, ArrayList<Int>>()
    var serviceCallback: (() -> Unit)? = null
    var isSearching = false
    var lock = Semaphore(1)
    var mqtt : MqttClient? = null
    companion object {
        private const val TAG = "AntToBleBridge"
    }
    @Synchronized
    fun startup(service: Context, callback: () -> Unit) {
        serviceCallback = callback
        stop()
        isSearching = true
        antDevices.clear()

        mqtt = MqttClient(service);
        mqtt!!.connect();


        runBlocking {
            lock.withPermit {
                antConnectors.add(HRConnector(service, object: AntDeviceConnector.DeviceManagerListener<AntDevice.HRDevice> {
                    override fun onDeviceStateChanged(result: RequestAccessResult, deviceState: DeviceState) {
                        if (deviceState == DeviceState.DEAD || deviceState == DeviceState.CLOSED || deviceState == DeviceState.SEARCHING) {
                            Log.i(TAG, "Bridge State Changed $result  $deviceState")
                          //  antDevices.clear()
                          //   selectedDevices.clear()
                          //  callback();
                        }
                    }

                    override fun onDataUpdated(data: AntDevice.HRDevice) {
                        dataUpdated(data, BleServiceType.HrService, callback) {
                            return@dataUpdated HRConnector(service, this)
                        }
                    }
                         override fun onCombinedSensor(antDeviceConnector: AntDeviceConnector<*, *>, deviceId: Int) {
                        // Not supported
                    }
                }))
                antConnectors.forEach { connector -> connector.startSearch() }
            }
        }

    }

    @Synchronized
    private fun dataUpdated(data: AntDevice, type: BleServiceType, serviceCallback: () -> Unit, createService: () -> AntDeviceConnector<*, *>) {
        val isNew = !antDevices.containsKey(data.deviceId)
        antDevices[data.deviceId] = data
        mqtt!!.publishMessage("ant", "${data.deviceId}");
        //bleServer?.updateData(type, data)
        if (isNew) {
            val connector = createService()
            runBlocking {
                lock.withPermit {
                    antConnectors.add(connector)
                }
            }
            //connector.startSearch()
        }

        // First selectedDevice selection
        if (!selectedDevices.containsKey(type)) {
            selectedDevices[type] = arrayListOf(data.deviceId)
            selectedDevicesUpdated()
        } else {
            if (type == BleServiceType.CscService) {
                // Bsc and Ble devices supported
                selectedDevices[type]?.let { devices ->
                    val existingDevice = selectedDevices[type]?.firstOrNull { antDevices[it]?.typeName == data.typeName }
                    if (existingDevice == null) {
                        devices.add(data.deviceId)
                        selectedDevicesUpdated()
                    }
                }
            }
        }
        serviceCallback()
    }

    @Synchronized
    fun deviceSelected(data: AntDevice) {
        val arrayList = selectedDevices[data.bleType] ?: arrayListOf()
        val existingDevice = arrayList.firstOrNull { antDevices[it]?.typeName == data.typeName }
        if (existingDevice != null) {
            arrayList.remove(existingDevice)
        }
        arrayList.add(data.deviceId)
        selectedDevicesUpdated()
        serviceCallback?.invoke()
    }

    private fun selectedDevicesUpdated() {
      // bleServer?.selectedDevices = selectedDevices
    }

    fun stop() {
        isSearching = false
        mqtt?.close()
        runBlocking {
            withContext(Dispatchers.IO) {
                lock.withPermit {
                    antConnectors.forEach { connector -> connector.stopSearch() }
                    antConnectors.clear()
                  //  bleServer?.stopServer()

                    serviceCallback = null
                }
            }
        }
    }
}