package com.example.data.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.database.AppDatabase
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Enterprise Accessibility Service for Remote Technical Assistance.
 * Performs simulated gestures, virtual key presses, and node click actions.
 * Integrates a WebSocket client to process incoming assistant JSON payloads safely.
 */
class DpcRemoteAssistanceService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var activePackageName: String? = null

    companion object {
        private const val TAG = "DpcRemoteAssistance"
        
        // Expose service status for UI indicators
        @Volatile
        var isServiceRunning = false
            private set

        // Static reference allows safe interaction when the service is active
        @Volatile
        var instance: DpcRemoteAssistanceService? = null
            private set

        val uiMirrorState = MutableStateFlow<String?>(null)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Accessibility service created.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        Log.d(TAG, "Accessibility service connected and active.")
        logEvent("Remote Assistance Active", "Enterprise Remote Technical Assistance Accessibility service connected.", "SUCCESS")
        
        // Start WebSocket listener automatically for active corporate remote assist endpoint
        startWebSocketListener("ws://127.0.0.1:8080/remote-assist")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty()) {
                activePackageName = pkg
            }
            
            // Only extract on meaningful user navigation / visual update events to optimize performance
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    extractAndSendUiTree()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted.")
        logEvent("Remote Assistance Interrupted", "Remote assistance session was interrupted.", "WARNING")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isServiceRunning = false
        stopWebSocket()
        Log.d(TAG, "Accessibility service destroyed.")
        logEvent("Remote Assistance Stopped", "Enterprise Remote Technical Assistance service deactivated.", "SUCCESS")
    }

    /**
     * Connects and listens to the designated Remote Assistance WebSocket channel
     */
    fun startWebSocketListener(url: String) {
        stopWebSocket()
        logEvent("WebSocket Connection Attempt", "Connecting to technical assistance channel: $url", "SUCCESS")
        
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logEvent("WebSocket Connected", "Technical assistance session established with corporate endpoint.", "SUCCESS")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                processRemoteCommand(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logEvent("WebSocket Closed", "Technical assistance channel closed: $reason (code: $code)", "SUCCESS")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logEvent("WebSocket Failure", "Technical assistance channel error: ${t.message}", "FAILED")
            }
        })
    }

    fun stopWebSocket() {
        webSocket?.close(1000, "Service shutting down")
        webSocket = null
    }

    /**
     * Process technical assistance commands (either from actual WebSocket or manual simulation)
     */
    fun processRemoteCommand(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val action = json.optString("action", "").lowercase()
            
            logEvent("Assistance Command Received", "Payload: $jsonString", "SUCCESS")

            when (action) {
                "back" -> {
                    performGlobalBackAction()
                }
                "home" -> {
                    performGlobalHomeAction()
                }
                "click" -> {
                    val x = json.optDouble("x", -1.0).toFloat()
                    val y = json.optDouble("y", -1.0).toFloat()
                    if (x >= 0f && y >= 0f) {
                        performClickGesture(x, y)
                    } else {
                        // Attempt focused element click if coordinates are missing
                        performFocusedClick()
                    }
                }
                else -> {
                    logEvent("Command Execution Failed", "Unsupported command action: '$action'", "FAILED")
                }
            }
        } catch (e: Exception) {
            logEvent("Command Parse Error", "Error parsing incoming remote command JSON: ${e.message}", "FAILED")
        }
    }

    /**
     * Simulates the Hardware Back Button click
     */
    fun performGlobalBackAction() {
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        if (success) {
            logEvent("Action Executed", "Simulated BACK navigation keypress successfully.", "SUCCESS")
        } else {
            logEvent("Action Failed", "Failed to simulate BACK navigation.", "FAILED")
        }
    }

    /**
     * Simulates the Hardware Home Button click
     */
    fun performGlobalHomeAction() {
        val success = performGlobalAction(GLOBAL_ACTION_HOME)
        if (success) {
            logEvent("Action Executed", "Simulated HOME navigation keypress successfully.", "SUCCESS")
        } else {
            logEvent("Action Failed", "Failed to simulate HOME navigation.", "FAILED")
        }
    }

    /**
     * Simulates a dynamic tap gesture at absolute screen coordinates (x, y)
     */
    fun performClickGesture(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            // Create a stroke of 100ms duration at coordinates
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    logEvent("Gesture Executed", "Injected click gesture successfully at position ($x, $y).", "SUCCESS")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    logEvent("Gesture Cancelled", "Injected click gesture at ($x, $y) was cancelled.", "FAILED")
                }
            }, null)
        } else {
            logEvent("Gesture Failed", "Dynamic coordinate click requires Android N (API 24) or above.", "FAILED")
        }
    }

    /**
     * Finds the currently focused node or clickable elements and performs a ACTION_CLICK action
     */
    fun performFocusedClick() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            logEvent("Action Failed", "Unable to retrieve root window content for focused click.", "FAILED")
            return
        }

        val clicked = findAndClickFocusedNode(rootNode)
        rootNode.recycle()

        if (clicked) {
            logEvent("Action Executed", "Performed ACTION_CLICK on currently focused accessibility node.", "SUCCESS")
        } else {
            logEvent("Action Failed", "No clickable focused accessibility nodes found.", "FAILED")
        }
    }

    private fun findAndClickFocusedNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isFocused && node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickFocusedNode(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun logEvent(action: String, details: String, status: String) {
        val database = AppDatabase.getDatabase(this)
        serviceScope.launch {
            database.complianceLogDao().insertLog(
                ComplianceLog(
                    action = action,
                    details = details,
                    status = status,
                    isSimulated = false
                )
            )
        }
    }

    /**
     * Extracts the active view hierarchy and transmits it over the connected WebSocket session
     * as a real-time text-based mirror of secure or standard screens.
     */
    fun extractAndSendUiTree() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                return
            }
            val elementsArray = JSONArray()
            val hierarchyTree = traverseAndExtract(rootNode, elementsArray)
            rootNode.recycle()

            val pkgName = activePackageName ?: rootNode.packageName?.toString() ?: ""

            val jsonResult = JSONObject().apply {
                put("action", "ui_mirror")
                put("packageName", pkgName)
                put("elements", elementsArray)
                put("hierarchy", hierarchyTree)
            }

            val jsonString = jsonResult.toString()
            
            // Transmit via existing WebSocket listener if active
            webSocket?.let { ws ->
                ws.send(jsonString)
            }

            // Expose the raw JSON extraction state for real-time visualization
            uiMirrorState.value = jsonString
        } catch (e: Exception) {
            Log.e(TAG, "Error performing dynamic UI tree data extraction: ${e.message}")
        }
    }

    /**
     * Recursive traversal algorithm that visits every AccessibilityNodeInfo in the tree
     * to extract structural properties.
     */
    private fun traverseAndExtract(node: AccessibilityNodeInfo, flatList: JSONArray): JSONObject {
        val nodeJson = JSONObject()
        
        val textVal = node.text?.toString() ?: ""
        val contentDescVal = node.contentDescription?.toString() ?: ""
        val viewIdVal = node.viewIdResourceName ?: ""
        val clickableVal = node.isClickable

        // Format extracted data with requested keys:
        // {"element": "Account Balance", "value": "$5,000", "id": "com.bank.app:id/balance_text"}
        nodeJson.put("text", textVal)
        nodeJson.put("contentDescription", contentDescVal)
        nodeJson.put("viewIdResourceName", viewIdVal)
        nodeJson.put("isClickable", clickableVal)
        
        nodeJson.put("element", if (contentDescVal.isNotEmpty()) contentDescVal else textVal)
        nodeJson.put("value", textVal)
        nodeJson.put("id", viewIdVal)
        nodeJson.put("clickable", clickableVal)

        flatList.put(nodeJson)

        val childrenArray = JSONArray()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childJson = traverseAndExtract(child, flatList)
            childrenArray.put(childJson)
            child.recycle()
        }
        nodeJson.put("children", childrenArray)

        return nodeJson
    }
}
