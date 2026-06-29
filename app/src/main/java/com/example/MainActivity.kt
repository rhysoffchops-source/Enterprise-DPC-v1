package com.example

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.provider.Settings
import com.example.data.service.DpcRemoteAssistanceService
import android.os.Bundle
import org.json.JSONObject
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.ComplianceLog
import com.example.domain.model.EnterpriseApp
import com.example.domain.model.PolicyState
import com.example.presentation.viewmodel.DpcViewModel
import com.example.presentation.viewmodel.DpcViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    DpcApp(
                        activity = this,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val statusMessage = if (resultCode == RESULT_OK) {
                "Work Profile Provisioning successful!"
            } else {
                "Work Profile Provisioning process canceled or rejected by the system."
            }
            Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DpcApp(
    activity: Activity,
    modifier: Modifier = Modifier,
    viewModel: DpcViewModel = viewModel(factory = DpcViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val policyState by viewModel.policyState.collectAsState()
    val logs by viewModel.complianceLogs.collectAsState()
    val apps by viewModel.enterpriseApps.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showSecurityResetConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enterprise DPC Console",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            )
        )

        // Sandbox Simulator Notification Banner
        ModeBannerSection(
            policyState = policyState,
            onToggleSimulation = { viewModel.toggleSimulationMode(it) }
        )

        // Tab Navigation Panel
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            val tabs = listOf(
                Triple("Status", Icons.Default.Info, "tab_status"),
                Triple("Policies", Icons.Default.Lock, "tab_policies"),
                Triple("Deploy Apps", Icons.Default.PlayArrow, "tab_apps"),
                Triple("Support", Icons.Default.Build, "tab_support")
            )
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = tab.first,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.second,
                            contentDescription = tab.first,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.testTag(tab.third)
                )
            }
        }

        // Selected View Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> StatusTabContent(
                    activity = activity,
                    policyState = policyState,
                    onProvision = { viewModel.initiateWorkProfileProvisioning(activity) },
                    onUpdateAsset = { tag, domain -> viewModel.updateAssetDetails(tag, domain) }
                )
                1 -> PoliciesTabContent(
                    policyState = policyState,
                    onToggleCamera = { viewModel.setCameraDisabled(it) },
                    onToggleScreenCapture = { viewModel.setScreenCaptureDisabled(it) },
                    onLockScreen = { viewModel.lockScreen() },
                    onWipeRequest = { showWipeConfirmDialog = true },
                    onPasswordPolicyUpdate = { length, quality ->
                        viewModel.setPasswordPolicy(length, quality)
                    },
                    onSecurityReset = { showSecurityResetConfirmDialog = true },
                    onGrantTargetedPermissions = { viewModel.grantTargetedPermissions(it) },
                    onGrantUniversalPermissions = { viewModel.grantUniversalPermissions() },
                    onToggleSecurityOverride = { viewModel.overrideSecurityFlagsAcrossDevice(it) }
                )
                2 -> DeployAppsTabContent(
                    apps = apps,
                    onInstall = { viewModel.installEnterpriseApp(it) },
                    onUpdate = { viewModel.updateEnterpriseApp(it) },
                    onUninstall = { viewModel.uninstallEnterpriseApp(it) }
                )
                3 -> SupportTabContent(
                    viewModel = viewModel,
                    policyState = policyState,
                    logs = logs,
                    onStartSharing = { viewModel.startRemoteScreenSharing(it) },
                    onStopSharing = { viewModel.stopRemoteScreenSharing() },
                    onCollectLogs = { viewModel.collectDeviceLogs() },
                    onClearLogs = { viewModel.clearLogs() }
                )
            }
        }
    }

    // Work Profile Wipe Confirmation
    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirmDialog = false
                        viewModel.wipeWorkProfile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_wipe_button")
                ) {
                    Text("Proceed & Wipe Container")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning icon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Confirm Work Profile Wipe")
                }
            },
            text = {
                Text(
                    "You are about to initiate an enterprise data wipe. If running in Live Mode, this will permanently remove all work profile applications, policies, corporate accounts, and stored enterprise files from this device."
                )
            }
        )
    }

    // Security Decommission/Repurpose Reset Confirmation
    if (showSecurityResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityResetConfirmDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showSecurityResetConfirmDialog = false
                        viewModel.performSecurityReset()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_security_reset_button")
                ) {
                    Text("Proceed & Reset Security")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset icon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Decommission & Reset Device")
                }
            },
            text = {
                Text(
                    "You are about to trigger a full administrative Security Reset for decommissioning/repurposing. This will programmatically clear any existing screen lock PIN, pattern, or password, remove all future passcode complexity requirements, and lift all DPC user restrictions so the device is open for its next user."
                )
            }
        )
    }
}

@Composable
fun ModeBannerSection(
    policyState: PolicyState,
    onToggleSimulation: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (policyState.isSimulatedMode) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (policyState.isSimulatedMode) Icons.Default.Info else Icons.Default.Warning,
                        contentDescription = "Status Information Icon",
                        tint = if (policyState.isSimulatedMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (policyState.isSimulatedMode) "Enterprise Sandbox Mode" else "Active Android Hardware APIs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (policyState.isSimulatedMode) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (policyState.isSimulatedMode) {
                        "Local database simulation active. Policies compile locally without requiring strict MDM hardware enrollment keys."
                    } else {
                        "Enforcing real-time policies. Security Exception alerts will trigger if DPC lacks active Device Owner privileges."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 14.sp,
                    color = if (policyState.isSimulatedMode) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = policyState.isSimulatedMode,
                onCheckedChange = onToggleSimulation,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                ),
                modifier = Modifier.testTag("toggle_simulation_switch")
            )
        }
    }
}

// ==================== TAB 0: DEVICE STATUS & ASSET INVENTORY ====================
@Composable
fun StatusTabContent(
    activity: Activity,
    policyState: PolicyState,
    onProvision: () -> Unit,
    onUpdateAsset: (String, String) -> Unit
) {
    var showEditAssetDialog by remember { mutableStateOf(false) }
    var assetInput by remember(policyState.assetTag) { mutableStateOf(policyState.assetTag) }
    var domainInput by remember(policyState.workDomain) { mutableStateOf(policyState.workDomain) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Hero Visual Banner
        Image(
            painter = painterResource(id = R.drawable.enterprise_banner),
            contentDescription = "Enterprise Managed Security Graphic",
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Provisioning Status Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Android Enterprise Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = if (policyState.isProfileOwner && policyState.isDeviceAdmin) {
                            Color(0xFFE8F5E9)
                        } else {
                            Color(0xFFFFF3E0)
                        },
                        contentColor = if (policyState.isProfileOwner && policyState.isDeviceAdmin) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFE65100)
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (policyState.isProfileOwner && policyState.isDeviceAdmin) "Enrolled" else "Incomplete",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Device Admin Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (policyState.isDeviceAdmin) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Device administrator status indicator",
                        tint = if (policyState.isDeviceAdmin) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Administrator Privilege: ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (policyState.isDeviceAdmin) "Authorized" else "Unauthorized",
                        fontSize = 13.sp,
                        color = if (policyState.isDeviceAdmin) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Profile Owner Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (policyState.isProfileOwner) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Profile owner status indicator",
                        tint = if (policyState.isProfileOwner) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Work Profile Integration: ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (policyState.isProfileOwner) "Provisioned" else "Not Provisioned",
                        fontSize = 13.sp,
                        color = if (policyState.isProfileOwner) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!policyState.isProfileOwner || !policyState.isDeviceAdmin) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onProvision,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("onboard_provision_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Onboard admin icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Work Profile Provisioning", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Device Specification & Hardware Inventory
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IT Hardware Specifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showEditAssetDialog = true },
                        modifier = Modifier.testTag("btn_edit_asset")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit corporate metadata info", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Inventory properties
                HardwareSpecItem(label = "Asset Registration Tag", value = policyState.assetTag, icon = Icons.Default.Info)
                HardwareSpecItem(label = "Corporate Network Domain", value = policyState.workDomain, icon = Icons.Default.Home)
                HardwareSpecItem(label = "Device Model Name", value = policyState.deviceModel, icon = Icons.Default.Settings)
                HardwareSpecItem(label = "Platform Release", value = policyState.osVersion, icon = Icons.Default.Settings)
                HardwareSpecItem(label = "Hardware Serial S/N", value = policyState.serialNumber, icon = Icons.Default.Info)
                HardwareSpecItem(label = "Local IP Address", value = policyState.ipAddress, icon = Icons.Default.Settings)
                
                Spacer(modifier = Modifier.height(12.dp))

                // Storage calculation
                Text(
                    text = "Storage Space Inventory",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val usedStorage = policyState.totalStorageGB - policyState.availableStorageGB
                val storagePercentage = (usedStorage / policyState.totalStorageGB).toFloat()

                LinearProgressIndicator(
                    progress = { storagePercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used: ${String.format(Locale.US, "%.1f", usedStorage)} GB of ${String.format(Locale.US, "%.1f", policyState.totalStorageGB)} GB",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(storagePercentage * 100).toInt()}% Used",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Battery Capacity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = "Battery Status", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Current Battery Level", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${policyState.batteryLevel}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Asset Tag Edit Dialog
    if (showEditAssetDialog) {
        AlertDialog(
            onDismissRequest = { showEditAssetDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAsset(assetInput, domainInput)
                        showEditAssetDialog = false
                    },
                    modifier = Modifier.testTag("save_asset_button")
                ) {
                    Text("Save Identity")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAssetDialog = false }) {
                    Text("Dismiss")
                }
            },
            title = { Text("Edit Asset Credentials") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = assetInput,
                        onValueChange = { assetInput = it },
                        label = { Text("Corporate Asset Tag") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = "Asset tag input icon") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("input_asset_tag"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Enterprise Domain") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = "Enterprise domain input icon") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_work_domain"),
                        singleLine = true
                    )
                }
            }
        )
    }
}

@Composable
fun HardwareSpecItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$label:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.8f)
        )
    }
}


// ==================== TAB 1: ACTIVE SECURITY POLICIES ====================
@Composable
fun PoliciesTabContent(
    policyState: PolicyState,
    onToggleCamera: (Boolean) -> Unit,
    onToggleScreenCapture: (Boolean) -> Unit,
    onLockScreen: () -> Unit,
    onWipeRequest: () -> Unit,
    onPasswordPolicyUpdate: (Int, Int) -> Unit,
    onSecurityReset: () -> Unit,
    onGrantTargetedPermissions: (String) -> Unit,
    onGrantUniversalPermissions: () -> Unit,
    onToggleSecurityOverride: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Active Security Configurations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Hardware Controls Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hardware Access Policies",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Camera Policy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Disable Enterprise Camera", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Completely disables device camera access inside the enterprise container.",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = policyState.isCameraDisabled,
                        onCheckedChange = onToggleCamera,
                        modifier = Modifier.testTag("toggle_camera_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Screen Capture Policy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block Screen Capture", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Restricts employees from taking screenshots or capturing recordings of work apps.",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = policyState.isScreenCaptureDisabled,
                        onCheckedChange = onToggleScreenCapture,
                        modifier = Modifier.testTag("toggle_screencap_switch")
                    )
                }
            }
        }

        // Passcode Strength Requirements
        var selectedLength by remember(policyState.passwordMinimumLength) {
            mutableFloatStateOf(policyState.passwordMinimumLength.coerceAtLeast(4).toFloat())
        }
        var selectedQuality by remember(policyState.passwordQuality) {
            mutableIntStateOf(policyState.passwordQuality)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Passcode Complexity Enforcement",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Length slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Minimum Password Length", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "${selectedLength.toInt()} Characters",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = selectedLength,
                    onValueChange = { selectedLength = it },
                    onValueChangeFinished = {
                        onPasswordPolicyUpdate(selectedLength.toInt(), selectedQuality)
                    },
                    valueRange = 4f..16f,
                    steps = 11,
                    modifier = Modifier.testTag("slider_password_length")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quality Selector
                Text("Lock Quality Category", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isNumeric = selectedQuality == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                    val isAlpha = selectedQuality == DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC

                    FilterChip(
                        selected = isNumeric,
                        onClick = {
                            selectedQuality = DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                            onPasswordPolicyUpdate(selectedLength.toInt(), selectedQuality)
                        },
                        label = { Text("Numeric / PIN") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_quality_numeric")
                    )

                    FilterChip(
                        selected = isAlpha,
                        onClick = {
                            selectedQuality = DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                            onPasswordPolicyUpdate(selectedLength.toInt(), selectedQuality)
                        },
                        label = { Text("Alphanumeric") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_quality_alpha")
                    )
                }
            }
        }

        // Administrative Commands
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Direct MDM Console Commands",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onLockScreen,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("action_lock_screen"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Trigger immediate screen lock", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock Screen", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onWipeRequest,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("action_wipe_profile"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Trigger enterprise data wipe", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wipe Profile", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Reset & Decommissioning Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Decommission Icon",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Device Decommissioning & Security Reset",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Safely repurpose or decommission this device. This high-priority administrative module clears existing passwords, removes complexity policies, and completely lifts system blocks.",
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSecurityReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("action_security_reset"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Security reset icon", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Execute Security Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Targeted Permission Provisioning Card
        var targetPkgName by remember { mutableStateOf("com.corporate.diagnostics") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Permission provisioning icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Targeted Permission Provisioning",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Provision critical system permissions to corporate diagnostic or inspection packages programmatically. This grants READ_SMS and ACCESS_FINE_LOCATION silently without technician prompts.",
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = targetPkgName,
                    onValueChange = { targetPkgName = it },
                    label = { Text("Target Package Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_targeted_package_name"),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Permissions to provision:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "READ_SMS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACCESS_FINE_LOCATION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onGrantTargetedPermissions(targetPkgName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_provision_permissions"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Grant permissions icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Provision Targeted Permissions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Spacer(modifier = Modifier.height(16.dp))

                // Universal Permission Granting Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Universal App Permission Grant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Audits and sequentially grants critical SMS, contacts, location and audio permissions to all installed user packages.",
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onGrantUniversalPermissions,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(36.dp)
                            .testTag("btn_universal_grant"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Spacer(modifier = Modifier.height(16.dp))

                // Security Flag Override Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device-Wide FLAG_SECURE Override",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Bypasses screen-capture and recording blocks (FLAG_SECURE) across all apps to permit remote assistance visual guidance.",
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = policyState.isSecurityFlagOverrideActive,
                        onCheckedChange = onToggleSecurityOverride,
                        modifier = Modifier.testTag("switch_security_flag_override")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}


// ==================== TAB 2: ENTERPRISE APP STORE DEPLOYMENT ====================
@Composable
fun DeployAppsTabContent(
    apps: List<EnterpriseApp>,
    onInstall: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Corporate Managed App Store",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Push required applications silently or revoke licenses for employee environments.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps) { app ->
                AppDeploymentItem(
                    app = app,
                    onInstall = { onInstall(app.id) },
                    onUpdate = { onUpdate(app.id) },
                    onUninstall = { onUninstall(app.id) }
                )
            }
        }
    }
}

@Composable
fun AppDeploymentItem(
    app: EnterpriseApp,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (app.name) {
                                "Slack Enterprise" -> Icons.Default.Email
                                "Cisco Webex Meetings" -> Icons.Default.PlayArrow
                                "Salesforce Mobile" -> Icons.Default.CheckCircle
                                "Secure Corp Email" -> Icons.Default.Email
                                "Workspace Hub" -> Icons.Default.Settings
                                else -> Icons.Default.Settings
                            },
                            contentDescription = app.name,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = app.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${app.packageName} • ${app.sizeMb} MB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // App status badge
                Surface(
                    color = when (app.status) {
                        "INSTALLED" -> Color(0xFFE8F5E9)
                        "PENDING_UPDATE" -> Color(0xFFFFF3E0)
                        "INSTALLING" -> Color(0xFFE1F5FE)
                        else -> Color(0xFFECEFF1)
                    },
                    contentColor = when (app.status) {
                        "INSTALLED" -> Color(0xFF2E7D32)
                        "PENDING_UPDATE" -> Color(0xFFE65100)
                        "INSTALLING" -> Color(0xFF0288D1)
                        else -> Color(0xFF546E7A)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (app.status) {
                            "INSTALLED" -> "Installed"
                            "PENDING_UPDATE" -> "Update Ready"
                            "INSTALLING" -> "Installing"
                            else -> "Available"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Version and Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current Version: ${app.version}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (app.status == "INSTALLING") {
                    Text(
                        text = "${app.progress.toInt()}% completed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (app.status == "INSTALLING") {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { app.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (app.status) {
                    "NOT_INSTALLED" -> {
                        Button(
                            onClick = onInstall,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Install button icon", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push Install", fontSize = 12.sp)
                        }
                    }
                    "PENDING_UPDATE" -> {
                        Row {
                            TextButton(
                                onClick = onUninstall,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Uninstall", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Update button icon", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Deploy Update", fontSize = 12.sp)
                            }
                        }
                    }
                    "INSTALLED" -> {
                        TextButton(
                            onClick = onUninstall,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete app icon", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Uninstall / Revoke License", fontSize = 12.sp)
                        }
                    }
                    "INSTALLING" -> {
                        // Keep user informed
                        Text(
                            text = "Processing secure package transaction...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}


// ==================== TAB 3: SUPPORT & DIAGNOSTIC AUDITING ====================
@Composable
fun SupportTabContent(
    viewModel: DpcViewModel,
    policyState: PolicyState,
    logs: List<ComplianceLog>,
    onStartSharing: (String) -> Unit,
    onStopSharing: () -> Unit,
    onCollectLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    var agentInput by remember { mutableStateOf("Enterprise Admin") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Upper Column with remote share controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Troubleshooting & Log Harvesting",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Screen Sharing card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Remote Technical Mirroring",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!policyState.isRemoteViewingActive) {
                        Text(
                            text = "Initiate an encrypted video stream. Corporate admins can assist and troubleshoot real-time compliance conflicts.",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = agentInput,
                            onValueChange = { agentInput = it },
                            label = { Text("Troubleshoot Operator Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Agent field icon") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_agent_name"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onStartSharing(agentInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_start_mirroring"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Mirror icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launch Screen Mirroring Session")
                        }
                    } else {
                        // Connection active display
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(0xFF2E7D32), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "MIRRORING STREAM ACTIVE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Operator: ${policyState.supportAgentName}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = "Session Token: ${policyState.supportSessionId}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Encoding: H.264 • 1080p @ 60 FPS • Encrypted Stream",
                                    fontSize = 10.sp,
                                    color = Color(0xFF2E7D32).copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onStopSharing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_stop_mirroring"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Stop icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Terminate Connection")
                        }
                    }
                }
            }

            // Remote Technical Assistance Accessibility Service Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Remote Technical Assistance (Accessibility API)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val isServiceActive = DpcRemoteAssistanceService.isServiceRunning
                    val serviceInstance = DpcRemoteAssistanceService.instance

                    // Status Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Surface(
                            color = if (isServiceActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isServiceActive) Color(0xFF2E7D32) else Color(0xFFC62828), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isServiceActive) "SERVICE ACTIVE" else "SERVICE DISABLED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isServiceActive) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServiceActive) "Active WebSocket Assist session" else "Requires Accessibility Permission",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isServiceActive) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_enable_accessibility"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Accessibility Settings", fontSize = 12.sp)
                        }
                    } else {
                        var wsUrl by remember { mutableStateOf("ws://127.0.0.1:8080/remote-assist") }
                        
                        OutlinedTextField(
                            value = wsUrl,
                            onValueChange = { wsUrl = it },
                            label = { Text("Corporate Assist WS URL", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("input_ws_url"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                serviceInstance?.startWebSocketListener(wsUrl)
                                Toast.makeText(context, "Initiating WS connection to $wsUrl", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_connect_ws"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Connect icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Remote WebSocket", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Assistance Payload Simulator",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Test command translation locally without an active WebSocket server connection:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Simulated Command Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val payload = "{\"action\": \"back\"}"
                                if (isServiceActive) {
                                    serviceInstance?.processRemoteCommand(payload)
                                } else {
                                    Toast.makeText(context, "Accessibility service is not active!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_sim_back"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Sim Back", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val payload = "{\"action\": \"home\"}"
                                if (isServiceActive) {
                                    serviceInstance?.processRemoteCommand(payload)
                                } else {
                                    Toast.makeText(context, "Accessibility service is not active!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_sim_home"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Sim Home", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Simulated Command Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val payload = "{\"action\": \"click\", \"x\": 500, \"y\": 1000}"
                                if (isServiceActive) {
                                    serviceInstance?.processRemoteCommand(payload)
                                } else {
                                    Toast.makeText(context, "Accessibility service is not active!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_sim_click_coord"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Tap (500, 1000)", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val payload = "{\"action\": \"click\"}"
                                if (isServiceActive) {
                                    serviceInstance?.processRemoteCommand(payload)
                                } else {
                                    Toast.makeText(context, "Accessibility service is not active!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_sim_click_focus"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Sim Click Focused", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Text-Based UI Tree Mirror Panel
            val isAssistanceActive = DpcRemoteAssistanceService.isServiceRunning
            val assistanceInstance = DpcRemoteAssistanceService.instance
            val uiMirrorState by DpcRemoteAssistanceService.uiMirrorState.collectAsState()
            val parsedMirror: Pair<String, List<Map<String, String>>> = remember(uiMirrorState) {
                val stateVal = uiMirrorState
                if (!stateVal.isNullOrEmpty()) {
                    try {
                        val json = JSONObject(stateVal)
                        val pkg = json.optString("packageName", "N/A")
                        val elements = json.optJSONArray("elements")
                        val list = mutableListOf<Map<String, String>>()
                        if (elements != null) {
                            for (i in 0 until elements.length()) {
                                val elObj = elements.getJSONObject(i)
                                val text = elObj.optString("text", "")
                                val contentDesc = elObj.optString("contentDescription", "")
                                val viewId = elObj.optString("viewIdResourceName", "")
                                val clickable = elObj.optBoolean("isClickable", false)
                                
                                if (text.isNotEmpty() || contentDesc.isNotEmpty() || viewId.isNotEmpty()) {
                                    list.add(mapOf(
                                        "text" to text,
                                        "contentDescription" to contentDesc,
                                        "viewId" to viewId,
                                        "clickable" to clickable.toString()
                                    ))
                                }
                            }
                        }
                        Pair(pkg, list)
                    } catch (e: Exception) {
                        Pair("Error Parsing", emptyList<Map<String, String>>())
                    }
                } else {
                    Pair("No Data", emptyList<Map<String, String>>())
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "UI Mirror Icon",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Real-Time Screen UI Mirror",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        // Force refresh button
                        IconButton(
                            onClick = {
                                if (isAssistanceActive) {
                                    assistanceInstance?.extractAndSendUiTree()
                                }
                            },
                            enabled = isAssistanceActive,
                            modifier = Modifier.size(24.dp).testTag("btn_refresh_ui_tree")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Force Extract UI tree",
                                tint = if (isAssistanceActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Provides a real-time text-based mirror of secure corporate screens by recursively traversing active view hierarchies. Indispensable for accessibility and guided navigation.",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isAssistanceActive) {
                        Text(
                            text = "Targeting Package: ${parsedMirror.first}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (parsedMirror.second.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Navigate to any screen or tap 'Force Refresh' to extract nodes",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Display list of extracted nodes
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(parsedMirror.second.size) { idx ->
                                    val item = parsedMirror.second[idx]
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                val rawId = item["viewId"] ?: ""
                                                val displayId = if (rawId.contains("/")) rawId.substringAfterLast('/') else if (rawId.isNotEmpty()) rawId else "None"
                                                Text(
                                                    text = "ID: $displayId",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                if (item["clickable"] == "true") {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "CLICKABLE",
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val txtVal = item["text"] ?: ""
                                            if (txtVal.isNotEmpty()) {
                                                Text(
                                                    text = "Value: \"$txtVal\"",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            val descVal = item["contentDescription"] ?: ""
                                            if (descVal.isNotEmpty()) {
                                                Text(
                                                    text = "Desc: \"$descVal\"",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Enable Accessibility Service to inspect secure screens",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostic Log Harvester
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Remote Log Collector",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Zips and uploads diagnostic dumps, error records, and policy logcats directly to the admin center.",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (policyState.isLogCollectionRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Harvesting logcat & dumpsys components...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${policyState.logCollectionProgress.toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { policyState.logCollectionProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        Button(
                            onClick = onCollectLogs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_collect_logs"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Harvest log icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Initiate Security Log Upload")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            AssetDiscoverySection(viewModel = viewModel)
            Spacer(modifier = Modifier.height(12.dp))
            EmergencySmsSignalingSection(viewModel = viewModel)
        }

        // Lower Column containing Audit log records list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MDM Audit Registry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = onClearLogs,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Wipe registry icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs")
                    }
                }
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Empty audit registry logo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Audit Registry Empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Actions, errors, and policy violations will compile diagnostic entries here.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { log ->
                        ComplianceLogItem(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun ComplianceLogItem(log: ComplianceLog) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_${log.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = when (log.status) {
                            "SUCCESS" -> Color(0xFFE8F5E9)
                            "SIMULATED" -> Color(0xFFE1F5FE)
                            "FAILED" -> Color(0xFFFFEBEE)
                            "PENDING" -> Color(0xFFFFFDE7)
                            else -> Color(0xFFFFF3E0)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (log.status) {
                        "SUCCESS" -> Icons.Default.Check
                        "SIMULATED" -> Icons.Default.Info
                        "FAILED" -> Icons.Default.Warning
                        "PENDING" -> Icons.Default.Refresh
                        else -> Icons.Default.Warning
                    },
                    contentDescription = log.status,
                    tint = when (log.status) {
                        "SUCCESS" -> Color(0xFF2E7D32)
                        "SIMULATED" -> Color(0xFF0288D1)
                        "FAILED" -> Color(0xFFC62828)
                        "PENDING" -> Color(0xFFFBC02D)
                        else -> Color(0xFFEF6C00)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.action,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                    val timeStr = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AssetDiscoverySection(
    viewModel: com.example.presentation.viewmodel.DpcViewModel
) {
    val context = LocalContext.current
    val isScanning by viewModel.assetDiscoveryManager.isScanning.collectAsState()
    val discoveredAssets by viewModel.assetDiscoveryManager.discoveredAssets.collectAsState()
    val policyState by viewModel.policyState.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Discovery icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Offline Asset Inventory Discovery",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Identifies and audits nearby company-owned devices using Wi-Fi Direct (WifiP2pManager) in completely offline remote sites.",
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (isScanning) {
                            viewModel.assetDiscoveryManager.stopScan()
                        } else {
                            viewModel.assetDiscoveryManager.startScan(policyState.isSimulatedMode)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_scan_assets"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Discovery Scan", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Scan icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Offline Assets", fontSize = 12.sp)
                    }
                }
            }

            if (discoveredAssets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nearby Discovered Devices (${discoveredAssets.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    discoveredAssets.forEach { asset ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = asset.deviceName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            // Corporate signature filter badge
                                            Surface(
                                                color = if (asset.isCorporate) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (asset.isCorporate) "CORP SIGNATURE" else "NON-CORP",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (asset.isCorporate) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Address: ${asset.deviceAddress}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (asset.assetTag.isNotEmpty()) {
                                            Text(
                                                text = "Verified Asset ID: ${asset.assetTag}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    when (asset.verificationStatus) {
                                        "UNVERIFIED" -> {
                                            Button(
                                                onClick = {
                                                    viewModel.assetDiscoveryManager.verifyAssetHandshake(asset.id)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondary
                                                ),
                                                modifier = Modifier
                                                    .height(30.dp)
                                                    .testTag("btn_verify_asset_${asset.id}"),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                            ) {
                                                Text("Verify Handshake", fontSize = 10.sp)
                                            }
                                        }
                                        "VERIFYING" -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 1.5.dp
                                                )
                                                Text(
                                                    text = "Verifying...",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        "VERIFIED" -> {
                                            Surface(
                                                color = Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Verified Icon",
                                                        tint = Color(0xFF2E7D32),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "VERIFIED",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                        }
                                        "FAILED" -> {
                                            Surface(
                                                color = Color(0xFFFFEBEE),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Failed Icon",
                                                        tint = Color(0xFFC62828),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "REJECTED",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFC62828)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Silent Patch Deployment block for verified assets
                                if (asset.verificationStatus == "VERIFIED") {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Build,
                                                    contentDescription = "Patch Icon",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Silent Patch Deployment",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            // Deployment Status Badge
                                            Surface(
                                                color = when (asset.patchDeploymentStatus) {
                                                    "DEPLOYING" -> MaterialTheme.colorScheme.secondaryContainer
                                                    "DEPLOYED" -> Color(0xFFE8F5E9)
                                                    "FAILED" -> Color(0xFFFFEBEE)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = when (asset.patchDeploymentStatus) {
                                                        "DEPLOYING" -> "DEPLOYING"
                                                        "DEPLOYED" -> "SUCCESSFUL"
                                                        "FAILED" -> "FAILED"
                                                        else -> "UPDATE PENDING"
                                                    },
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (asset.patchDeploymentStatus) {
                                                        "DEPLOYING" -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        "DEPLOYED" -> Color(0xFF2E7D32)
                                                        "FAILED" -> Color(0xFFC62828)
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Verify signature & silently install 'com.corporate.compliance.patch' via PackageInstaller and DevicePolicyManager authorization.",
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        when (asset.patchDeploymentStatus) {
                                            "NOT_STARTED" -> {
                                                Button(
                                                    onClick = {
                                                        viewModel.assetDiscoveryManager.deploySilentPatch(asset.id)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(32.dp)
                                                        .testTag("btn_deploy_patch_${asset.id}"),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Install",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Deploy Silent Patch Update", fontSize = 11.sp)
                                                }
                                            }
                                            "DEPLOYING" -> {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    LinearProgressIndicator(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Transferring silent patch APK and executing PackageInstaller.SessionParams...",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            "DEPLOYED" -> {
                                                Surface(
                                                    color = Color(0xFFE8F5E9),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Success",
                                                            tint = Color(0xFF2E7D32),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = "Patch deployed. Verification callback received. APK was installed silently with INSTALL_REASON_UPDATE.",
                                                            fontSize = 10.sp,
                                                            lineHeight = 12.sp,
                                                            color = Color(0xFF2E7D32)
                                                        )
                                                    }
                                                }
                                            }
                                            "FAILED" -> {
                                                Button(
                                                    onClick = {
                                                        viewModel.assetDiscoveryManager.deploySilentPatch(asset.id)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(32.dp)
                                                ) {
                                                    Text("Retry Silent Patch", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencySmsSignalingSection(
    viewModel: com.example.presentation.viewmodel.DpcViewModel
) {
    val context = LocalContext.current
    var targetPhone by remember { mutableStateOf("") }
    var signalMessage by remember { mutableStateOf("RECOVERY_SIGNAL_TRIGGER_SECURE_BOOT") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.sendEmergencySms(targetPhone, signalMessage)
        } else {
            Toast.makeText(context, "Permission Denied: SEND_SMS permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "SMS icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Emergency Device Signaling",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Send a manual recovery or diagnostics command text signal to a specific offline corporate device using direct SMS.",
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = targetPhone,
                onValueChange = { targetPhone = it },
                label = { Text("Offline Target Phone Number", fontSize = 12.sp) },
                placeholder = { Text("+15550199", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_emergency_phone"),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = signalMessage,
                onValueChange = { signalMessage = it },
                label = { Text("Plain Text Signal Message", fontSize = 12.sp) },
                placeholder = { Text("RECOVERY_SIGNAL_BOOT_RECOVERY", fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_emergency_message"),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (targetPhone.isBlank() || signalMessage.isBlank()) {
                        Toast.makeText(context, "Error: Phone and message cannot be empty.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.sendEmergencySms(targetPhone, signalMessage)
                    } else {
                        permissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_send_emergency_sms"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send Icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transmit Recovery SMS Signal", fontSize = 13.sp)
            }
        }
    }
}

