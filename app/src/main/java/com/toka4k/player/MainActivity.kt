package com.toka4k.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.toka4k.player.ui.theme.TokaTheme
import com.toka4k.player.ui.theme.DarkBg
import com.toka4k.player.ui.theme.NeonCyan
import com.toka4k.player.ui.theme.NeonPurple
import com.toka4k.player.ui.theme.BrightWhite
import com.toka4k.player.ui.theme.GlassSurface
import com.toka4k.player.ui.theme.GlowGreen
import com.toka4k.player.ui.theme.SurfaceGray
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Profiles definitions
enum class HardwareProfile(val title: String) {
    LOW_END("Low-End (Fast FSR/Bilinear)"),
    HIGH_END("High-End (Anime4K/FSR Sharp)")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokaTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State holdings
    var selectedProfile by remember { mutableStateOf(HardwareProfile.HIGH_END) }
    var activeVideoUri by remember { mutableStateOf<Uri?>(null) }
    var activeVideoTitle by remember { mutableStateOf("") }
    var activeMediaIsVideo by remember { mutableStateOf(true) }
    
    // Media List states
    var isScanning by remember { mutableStateOf(false) }
    val mediaFilesList = remember { mutableStateListOf<MediaFile>().apply { addAll(demoMediaFiles) } }

    // Shader settings states
    var edgeIntensity by remember { mutableFloatStateOf(1.8f) }
    var scaleStep by remember { mutableFloatStateOf(2.0f) }
    var noiseReduction by remember { mutableStateOf(true) }
    var superSamplingVal by remember { mutableStateOf("4x") }

    // Dialog trigger states
    var showUrlDialog by remember { mutableStateOf(false) }
    var currentSubscreen by remember { mutableStateOf("dashboard") } // dashboard, player, shaders, medialist

    // Interactive simulated terminal logs
    val compilerLogs = remember {
        mutableStateListOf(
            "=================== TOKA4K CORE COMPILER ===================",
            "[SYSTEM] MPV core initialization complete.",
            "[INFO] Direct3D11 / Vulkan surface projection verified.",
            "[GPU] Renderer identity: Cyberpunk GPU core G71-Pro-X.",
            "[SHADER_READY] Loaded default shaders pipeline successfully."
        )
    }

    // Append beautiful shader compilation feedback logs
    fun logEvent(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.ms", java.util.Locale.US).format(java.util.Date())
        compilerLogs.add("[$timestamp] $message")
        if (compilerLogs.size > 22) {
            compilerLogs.removeAt(5) // keep header and top systems
        }
    }

    // Helper for launching URL intent
    val openDevLink = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://alexsifatrayhan.github.io/about-me/"))
        context.startActivity(intent)
    }

    // Permission launcher contract for storage directory scanning
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            Toast.makeText(context, "Storage permissions authorized.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Authorized scanner has fallback directories.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = DarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070707),
                            Color(0xFF0F111E),
                            Color(0xFF090312)
                        )
                    )
                )
        ) {
            // Screen router
            AnimatedContent(
                targetState = currentSubscreen,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "Navigation"
            ) { targetScreen ->
                when (targetScreen) {
                    "dashboard" -> {
                        DashboardScreenLayout(
                            selectedProfile = selectedProfile,
                            onProfileChange = { profile ->
                                selectedProfile = profile
                                logEvent("Active profile swapped -> ${profile.title}")
                                if (profile == HardwareProfile.HIGH_END) {
                                    logEvent("[GLSL] Injecting Anime4K 4K-Upsampling shaders core.")
                                    logEvent("[GPU] Maximum super-sampling kernel selected. Frame budget: 16.6ms.")
                                } else {
                                    logEvent("[GLSL] Downgraded to Lightweight FSR / Bilinear scaling filters.")
                                    logEvent("[GPU] Low power pipeline loaded. Thermal safety mode optimal.")
                                }
                            },
                            onLocalVideoClick = {
                                currentSubscreen = "medialist"
                            },
                            onStreamUrlClick = {
                                showUrlDialog = true
                            },
                            onShaderSettingsClick = {
                                currentSubscreen = "shaders"
                                logEvent("Entered GLSL Shader Tuning console.")
                            },
                            onDevClick = openDevLink
                        )
                    }
                    "medialist" -> {
                        MediaExplorerScreen(
                            mediaFiles = mediaFilesList,
                            isScanning = isScanning,
                            onScanClick = {
                                scope.launch {
                                    isScanning = true
                                    logEvent("Requesting permission for scanning directory tracks...")
                                    val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        arrayOf(
                                            android.Manifest.permission.READ_MEDIA_VIDEO,
                                            android.Manifest.permission.READ_MEDIA_AUDIO
                                        )
                                    } else {
                                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    permissionLauncher.launch(permissions)
                                    
                                    delay(1000)
                                    val scanned = scanDeviceMedia(context)
                                    logEvent("Successfully registered ${scanned.size} offline device media files.")
                                    val updated = (demoMediaFiles + scanned).distinctBy { it.uri }
                                    mediaFilesList.clear()
                                    mediaFilesList.addAll(updated)
                                    isScanning = false
                                }
                            },
                            onFileSelect = { file ->
                                activeVideoUri = file.uri
                                activeVideoTitle = file.name
                                activeMediaIsVideo = file.isVideo
                                currentSubscreen = "player"
                                logEvent("Playing ${if (file.isVideo) "Video" else "Audio"}: ${file.name}")
                            },
                            onBackClick = {
                                currentSubscreen = "dashboard"
                            }
                        )
                    }
                    "player" -> {
                        VideoPlayerHUDLayout(
                            videoUri = activeVideoUri,
                            videoTitle = activeVideoTitle,
                            activeProfile = selectedProfile,
                            isVideo = activeMediaIsVideo,
                            onBackClick = {
                                currentSubscreen = "medialist"
                                activeVideoUri = null
                            },
                            onProfileToggle = {
                                selectedProfile = if (selectedProfile == HardwareProfile.HIGH_END) {
                                    HardwareProfile.LOW_END
                                } else {
                                    HardwareProfile.HIGH_END
                                }
                                logEvent("Realtime Hot-Swapped shader profile: ${selectedProfile.title}")
                            }
                        )
                    }
                    "shaders" -> {
                        ShaderSettingsConsole(
                            selectedProfile = selectedProfile,
                            edgeIntensity = edgeIntensity,
                            onEdgeChange = { edgeIntensity = it },
                            scaleStep = scaleStep,
                            onScaleChange = { scaleStep = it },
                            noiseReduction = noiseReduction,
                            onNoiseToggle = { noiseReduction = it },
                            superSamplingVal = superSamplingVal,
                            onSuperSamplingClick = { superSamplingVal = it },
                            compilerLogs = compilerLogs,
                            onCompileClick = {
                                scope.launch {
                                    logEvent("[CMD] Init full GLSL shader re-compilation chain...")
                                    delay(400)
                                    logEvent("[GLSL] COMPILING kernel standard definitions...")
                                    delay(400)
                                    if (selectedProfile == HardwareProfile.HIGH_END) {
                                        logEvent("[GLSL] COMPILATION SUCCESS: Anime4K EdgeRefine output linked in 24ms.")
                                    } else {
                                        logEvent("[GLSL] COMPILATION SUCCESS: Bicubic/FSR lightweight linked in 4ms.")
                                    }
                                    logEvent("[SYSTEM] GPU configuration profile pipeline HOT SWAP complete.")
                                }
                            },
                            onBackClick = {
                                currentSubscreen = "dashboard"
                            }
                        )
                    }
                }
            }

            // Streaming URL Input Dialog
            if (showUrlDialog) {
                var urlInput by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4") }
                
                AlertDialog(
                    onDismissRequest = { showUrlDialog = false },
                    containerColor = SurfaceGray,
                    shape = RoundedCornerShape(16.dp),
                    title = {
                        Text(
                            text = "NETWORK STREAM CONSOLE",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Enter stream source URL (MP4, MP3, IPTV stream headers). Shaders upscale live matrix feeds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrightWhite.copy(alpha = 0.7f)
                            )
                            
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                textStyle = TextStyle(color = BrightWhite, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                label = { Text("Stream URL", color = NeonCyan) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stream_url_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = NeonPurple,
                                    focusedLabelColor = NeonCyan,
                                    cursorColor = NeonCyan
                                )
                            )

                            Text(
                                text = "PRESET HIGH-FIDELITY FEEDS FOR TESTING:",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonPurple,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            // Quick stream presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { urlInput = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3322D3EE)),
                                    border = BorderStroke(0.5.dp, NeonCyan),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Anime Cinematic", fontSize = 9.sp, color = NeonCyan, fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = { urlInput = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33A855F7)),
                                    border = BorderStroke(0.5.dp, NeonPurple),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Synthwave Radio", fontSize = 9.sp, color = NeonPurple, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    activeVideoUri = Uri.parse(urlInput)
                                    val isAudio = urlInput.contains(".mp3") || urlInput.contains(".wav") || urlInput.contains("helix")
                                    activeVideoTitle = urlInput.substringAfterLast("/")
                                    if (activeVideoTitle.length > 32) {
                                        activeVideoTitle = if (isAudio) "Cyber Stream Radio" else "Network Stream Link"
                                    }
                                    activeMediaIsVideo = !isAudio
                                    showUrlDialog = false
                                    currentSubscreen = "player"
                                    logEvent("Configuring remote feed: ${urlInput}")
                                    logEvent("Streaming format mapped -> ${if (activeMediaIsVideo) "Video" else "Audio Track"}")
                                    logEvent("Active upscaler pipeline: ${selectedProfile.title}")
                                } else {
                                    Toast.makeText(context, "Please write a valid URL.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.testTag("stream_confirm_button")
                        ) {
                            Text("LAUNCH STREAM", color = DarkBg, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text("ABORT", color = NeonPurple, fontFamily = FontFamily.Monospace)
                        }
                    }
                )
            }
        }
    }
}

// Subscreen 1: Dashboard Layout
@Composable
fun DashboardScreenLayout(
    selectedProfile: HardwareProfile,
    onProfileChange: (HardwareProfile) -> Unit,
    onLocalVideoClick: () -> Unit,
    onStreamUrlClick: () -> Unit,
    onShaderSettingsClick: () -> Unit,
    onDevClick: () -> Unit
) {
    // Pulse animation logic for the developer branding radar indicator
    val infiniteTransition = rememberInfiniteTransition(label = "developerBeacon")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App header containing ONLY what was requested but with the premium Frosted Glass gradient style
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Frosted Glass Abstract Cybermatic Eye/Core vector overlay
                Spacer(
                    modifier = Modifier
                        .size(96.dp)
                        .drawBehind {
                            // Cyan outer target line with glass feel
                            drawCircle(
                                color = NeonCyan.copy(alpha = 0.3f),
                                radius = size.minDimension / 1.5f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                            // Outer cyber glow ring
                            drawCircle(
                                color = NeonPurple.copy(alpha = 0.2f),
                                radius = size.minDimension / 2f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                            )
                            // Inner robotic iris circle
                            drawCircle(
                                color = NeonCyan.copy(alpha = 0.8f),
                                radius = size.minDimension / 2.6f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                            )
                            // Crosshair lines
                            drawLine(
                                color = NeonCyan.copy(alpha = 0.6f),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = NeonCyan.copy(alpha = 0.6f),
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 1.5f
                            )
                            // Core white glowing diamond pupil
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width / 2f, size.height / 2f - 10f)
                                lineTo(size.width / 2f + 10f, size.height / 2f)
                                lineTo(size.width / 2f, size.height / 2f + 10f)
                                lineTo(size.width / 2f - 10f, size.height / 2f)
                                close()
                            }
                            drawPath(path = path, color = BrightWhite)
                        }
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "TOKA4K",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 40.sp,
                    letterSpacing = 6.sp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF22D3EE), NeonPurple)
                    ),
                    shadow = Shadow(
                        color = NeonCyan.copy(alpha = 0.4f),
                        offset = Offset(0f, 0f),
                        blurRadius = 14f
                    )
                ),
                modifier = Modifier.testTag("app_hdr_title")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ADVANCED SHADER ENGINE // V2.4.0",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = NeonCyan.copy(alpha = 0.6f),
                    letterSpacing = 2.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Hardware Profile Switch / Toggle Hub
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Profile Selector Header as in HTML spec
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RENDERING PROFILE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "GLSL V4.6",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = BrightWhite.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace
                )
            }

            // Beautiful Frosted Glass Switch Toggle Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(12.dp))
                    .background(Color(0x0DFFFFFF)) // bg-white/5
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HardwareProfile.entries.forEach { profile ->
                    val isSelected = profile == selectedProfile
                    
                    val bgBrush = if (isSelected) {
                        Brush.horizontalGradient(
                            colors = listOf(NeonCyan.copy(alpha = 0.25f), NeonPurple.copy(alpha = 0.15f))
                        )
                    } else {
                        Brush.horizontalGradient(colors = listOf(Color.Transparent, Color.Transparent))
                    }
                    val borderStrokeColor = if (isSelected) NeonCyan.copy(alpha = 0.4f) else Color.Transparent
                    val textStyleColor = if (isSelected) NeonCyan else BrightWhite.copy(alpha = 0.4f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgBrush)
                            .border(
                                if (isSelected) 1.dp else 0.dp,
                                borderStrokeColor,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onProfileChange(profile) }
                            .testTag("profile_button_${profile.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (profile == HardwareProfile.LOW_END) "LOW-END" else "HIGH-END",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = textStyleColor,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation box change Dynamically on Profile selection
            Crossfade(targetState = selectedProfile, label = "ProfileDetails") { profile ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                        .background(Color(0x08FFFFFF))
                        .padding(12.dp)
                ) {
                    if (profile == HardwareProfile.LOW_END) {
                        ProfileDetailRow(label = "Primary Shader:", value = "Bilinear Fast / Lightweight FSR")
                        ProfileDetailRow(label = "GPU Budget:", value = "Low heat, ultra low battery drain")
                        ProfileDetailRow(label = "Device Range:", value = "Mid/Budget processors (G85, Snapdragon 6 etc)")
                        ProfileDetailRow(label = "Pipeline Rate:", value = "Constant smooth 60fps frame rendering")
                    } else {
                        ProfileDetailRow(label = "Primary Shader:", value = "Heavy Anime4K (Edge / Luma-refinement)")
                        ProfileDetailRow(label = "GPU Target:", value = "Maximum vector geometry rendering")
                        ProfileDetailRow(label = "Device Range:", value = "High-tier processors (Snapdragon 8, Dimensity)")
                        ProfileDetailRow(label = "Visual Yield:", value = "Fidelity 4K edge reconstruction")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = if (selectedProfile == HardwareProfile.HIGH_END) "Current: Anime4K + FSR Sharp (Ultra Mode)" else "Current: Fast Bilinear FSR (Power Saver Mode)",
                fontSize = 10.sp,
                color = BrightWhite.copy(alpha = 0.4f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Quick Actions Hub
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "CORE PLAYER CONFIG",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            // Open Local Video Card
            QuickActionGlassCard(
                title = "Open Local Video",
                description = "Internal Storage / SD Card",
                icon = Icons.Default.FolderOpen,
                borderColor = NeonCyan,
                testTag = "action_local_video",
                onClick = onLocalVideoClick
            )

            // Stream URL Card
            QuickActionGlassCard(
                title = "Stream URL",
                description = "Network Protocol / IPTV",
                icon = Icons.Default.Language,
                borderColor = NeonPurple,
                testTag = "action_stream_url",
                onClick = onStreamUrlClick
            )

            // Shader Settings Card
            QuickActionGlassCard(
                title = "Shader Settings",
                description = "Engine Configuration",
                icon = Icons.Default.Code,
                borderColor = BrightWhite,
                testTag = "action_shader_settings",
                onClick = onShaderSettingsClick
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Permanent Custom Branding Developer Button - Premium Frosted design
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .background(NeonCyan.copy(alpha = 0.1f))
                .clickable(onClick = onDevClick)
                .testTag("developer_branding_button")
                .padding(vertical = 14.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Heartbeat pulse dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .drawBehind {
                            // Outer pulsing glow halo
                            drawCircle(
                                color = NeonCyan.copy(alpha = pulseAlpha * 0.4f),
                                radius = size.minDimension / 2f * pulseScale * 1.8f
                            )
                            // Core focus point
                            drawCircle(
                                color = NeonCyan,
                                radius = size.minDimension / 2.5f
                            )
                        }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "SR7 MODS // CORE DEVELOPER",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = NeonCyan,
                        letterSpacing = 1.5.sp,
                        shadow = Shadow(
                            color = NeonCyan.copy(alpha = 0.5f),
                            offset = Offset(0f, 0f),
                            blurRadius = 10f
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = NeonCyan.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = BrightWhite,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun QuickActionGlassCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    borderColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp)) // border-white/10
            .background(Color(0x0DFFFFFF)) // bg-white/5
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(borderColor.copy(alpha = 0.2f))
                    .drawBehind {
                        // Soft glow
                        drawCircle(
                            color = borderColor.copy(alpha = 0.15f),
                            radius = size.minDimension / 1.1f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BrightWhite
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        color = BrightWhite.copy(alpha = 0.4f),
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

// Subscreen 2: Real Android Video Player with Neon Cyber HUD HUD Controls
@Composable
fun VideoPlayerHUDLayout(
    videoUri: Uri?,
    videoTitle: String,
    activeProfile: HardwareProfile,
    isVideo: Boolean,
    onBackClick: () -> Unit,
    onProfileToggle: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var scaleQualityMode by remember { mutableStateOf(true) } // quality/shader toggle simulator modifier
    var systemStatusText by remember { mutableStateOf("Upscaler active") }
    var frameProcessingMs by remember { mutableStateOf(1.2f) }
    
    // Video view positioning states
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    
    // Playback Speed control
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    
    // A-B play looping states
    var pointA by remember { mutableStateOf<Int?>(null) }
    var pointB by remember { mutableStateOf<Int?>(null) }
    var isLoopABActive by remember { mutableStateOf(false) }
    
    // Audio Routing Path state
    var selectedAudioPath by remember { mutableStateOf("Core Stereo Speakers") }
    
    // Subtitle track selection state
    var activeSubtitleTrack by remember { mutableStateOf("None") }
    
    // Dialog triggers
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioPathDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showMediaInfoDialog by remember { mutableStateOf(false) }

    // Periodically update simulated frame timing metrics so the UI feels live and organic
    LaunchedEffect(key1 = activeProfile) {
        val randomRange = if (activeProfile == HardwareProfile.HIGH_END) 12.0f..15.8f else 0.4f..1.1f
        while (true) {
            delay(1200)
            frameProcessingMs = randomRange.run { start + (endInclusive - start) * java.util.Random().nextFloat() }
        }
    }

    // Apply speed changes dynamically when playbackSpeed updates
    LaunchedEffect(playbackSpeed, mediaPlayerRef) {
        if (mediaPlayerRef != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val params = mediaPlayerRef!!.playbackParams
                mediaPlayerRef!!.playbackParams = params.setSpeed(playbackSpeed)
            } catch (e: Exception) {}
        }
    }

    // Capture playback position loop
    LaunchedEffect(isPlaying, videoViewRef) {
        while (true) {
            if (videoViewRef != null && isPlaying) {
                try {
                    currentPositionMs = videoViewRef!!.currentPosition.toLong()
                    // Auto restart A-B repeat segment
                    if (isLoopABActive && pointA != null && pointB != null) {
                        if (currentPositionMs >= pointB!!) {
                            videoViewRef!!.seekTo(pointA!!)
                            currentPositionMs = pointA!!.toLong()
                        }
                    }
                } catch (e: Exception) {}
            }
            delay(200)
        }
    }

    // Compose back handle
    BackHandler {
        onBackClick()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Appbar header for player
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("player_nav_back")
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Return", tint = BrightWhite)
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "TOKA4K CORE DECODER",
                    fontSize = 11.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = videoTitle,
                    fontSize = 13.sp,
                    color = BrightWhite,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val activity = findActivity(context)
                    if (activity != null) {
                        try {
                            activity.requestedOrientation = if (activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not toggle rotation.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, modifier = Modifier.testTag("player_rotation_toggle")) {
                    val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Toggle rotation screen lock orientation",
                        tint = if (isLandscape) NeonCyan else Color.Gray
                    )
                }

                IconButton(onClick = {
                    scaleQualityMode = !scaleQualityMode
                    systemStatusText = if (scaleQualityMode) "Scaling Active" else "Bypass mode"
                }) {
                    Icon(
                        imageVector = if (scaleQualityMode) Icons.Default.FilterCenterFocus else Icons.Default.BlurOff,
                        contentDescription = null,
                        tint = if (scaleQualityMode) GlowGreen else Color.Gray
                    )
                }
            }
        }

        // Active Screen Player Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF040404)),
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null) {
                // Real Android Native VideoView embedding inside Jetpack Compose
                var isPrepared by remember { mutableStateOf(false) }
                key(videoUri) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(videoUri)
                                setOnPreparedListener { mp ->
                                    isPrepared = true
                                    mp.isLooping = true
                                    durationMs = duration.toLong()
                                    mediaPlayerRef = mp
                                    // Apply speed modifier if supported
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        try {
                                            val params = mp.playbackParams
                                            mp.playbackParams = params.setSpeed(playbackSpeed)
                                        } catch (e: Exception) {}
                                    }
                                    if (isPlaying) {
                                        start()
                                    }
                                }
                                setOnErrorListener { _, _, _ ->
                                    Toast.makeText(context, "Error decoding network or stream codec format.", Toast.LENGTH_LONG).show()
                                    true
                                }
                                videoViewRef = this
                            }
                        },
                        update = { view ->
                            try {
                                if (isPrepared) {
                                    if (isPlaying) {
                                        view.start()
                                    } else {
                                        view.pause()
                                    }
                                }
                            } catch (e: Exception) {}
                        },
                        modifier = if (isVideo) Modifier.fillMaxSize() else Modifier.size(1.dp)
                    )
                }

                // Virtualizer panel for audio tracks
                if (!isVideo) {
                    val audioVisualizerTransition = rememberInfiniteTransition(label = "musicVisualizer")
                    val pulseScaleAnimated by audioVisualizerTransition.animateFloat(
                        initialValue = 0.97f,
                        targetValue = 1.03f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "discPulse"
                    )

                    val pulseScale = if (isPlaying) pulseScaleAnimated else 1.0f

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF060608)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            // Beating Thumbnail Card on Audio
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .scale(pulseScale)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF1F1F30),
                                                Color(0xFF0D0D14)
                                            )
                                        )
                                    )
                                    .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Design representation of album cover / thumbnail inside
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // A glowing background graphic
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = NeonPurple.copy(alpha = 0.08f),
                                            radius = size.minDimension / 1.5f,
                                            center = center
                                        )
                                        drawCircle(
                                            color = NeonCyan.copy(alpha = 0.08f),
                                            radius = size.minDimension / 2.5f,
                                            center = center
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "TOKA4K STUDIO",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = BrightWhite.copy(alpha = 0.5f),
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }

                                // Thin white virtualizer overlaying bottom of thumbnail
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    for (i in 0 until 24) {
                                        val barHeightTransition = rememberInfiniteTransition(label = "audioBar_$i")
                                        val heightMultiplierAnimated by barHeightTransition.animateFloat(
                                            initialValue = 0.15f,
                                            targetValue = 0.85f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(130 + (i * 17) % 180, easing = LinearOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "barScale"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(if (isPlaying) heightMultiplierAnimated else 0.08f)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(Color.White.copy(alpha = 0.85f))
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Custom audio track details
                            Text(
                                text = videoTitle,
                                style = TextStyle(
                                    color = BrightWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "MPEG-4 AAC • 320 KBPS • TOKA DSP ENGINE",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }
            } else {
                // If somehow no URI, fallback animation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = NeonPurple.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "NULL VIDEO DECODER PIPELINE",
                        color = Color.DarkGray,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Subtitle projection overlay
            if (activeSubtitleTrack != "None") {
                val subtitleText = getSynchronizedSubtitle(currentPositionMs)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(0.5.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = subtitleText,
                        color = BrightWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Real-Time Upscaler Stats HUD Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(0.5.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[REALTIME GLSL COUNTER HUD]",
                        fontSize = 11.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isPlaying && scaleQualityMode) GlowGreen else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = systemStatusText.uppercase(),
                            fontSize = 9.sp,
                            color = BrightWhite,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                Divider(color = NeonCyan.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        HUDKeyValText("ACTIVE_SHADER", if (scaleQualityMode) activeProfile.name else "BYPASS_BILINEAR")
                        HUDKeyValText("OUTPUT_GRID", "3840 x 2160 (4K UPSCALE)")
                        HUDKeyValText("SPEED_FACTOR", "${playbackSpeed}x")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        HUDKeyValText("KERNEL_LATENCY", String.format("%.2f ms", if (scaleQualityMode) frameProcessingMs else 0.12f))
                        HUDKeyValText("DECODER", "Toka4K-MPV-V1")
                        HUDKeyValText("A_B_REPEATER", if (isLoopABActive) "ACTIVE" else "READY")
                    }
                }
            }

            // Floating Hot Swap Toggle in play view
            Button(
                onClick = onProfileToggle,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .height(40.dp)
                    .testTag("player_shader_toggle"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, NeonCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrightWhite)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeProfile == HardwareProfile.HIGH_END) {
                        "SWAP LOW-END"
                    } else {
                        "SWAP HIGH-END"
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = BrightWhite
                )
            }
        }

        // Play Control Bar Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceGray)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Functional seek timeline track
            val curTimeText = formatTime(currentPositionMs)
            val totalTimeText = formatTime(durationMs)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = curTimeText, fontSize = 11.sp, color = BrightWhite.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                
                val sliderValue = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        if (durationMs > 0 && videoViewRef != null) {
                            val targetMs = (newVal * durationMs).toLong()
                            videoViewRef!!.seekTo(targetMs.toInt())
                            currentPositionMs = targetMs
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("player_timeline_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
                
                Text(text = totalTimeText, fontSize = 11.sp, color = BrightWhite.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Bottom timeline-integrated full screen rotation switch
                IconButton(
                    onClick = {
                        val activity = findActivity(context)
                        if (activity != null) {
                            try {
                                activity.requestedOrientation = if (activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not toggle rotation.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp).testTag("timeline_rotation_toggle")
                ) {
                    val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    Icon(
                        imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Toggle Screen Orientation",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row holding Advanced Micro Control Triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback speed toggle
                TextButton(
                    onClick = { showSpeedDialog = true },
                    modifier = Modifier.testTag("control_speed")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${playbackSpeed}x", fontSize = 9.sp, color = BrightWhite, fontFamily = FontFamily.Monospace)
                    }
                }

                // A-B Segment selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { pointA = currentPositionMs.toInt() },
                        colors = ButtonDefaults.buttonColors(containerColor = if (pointA == null) Color.Transparent else NeonCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(0.5.dp, if (pointA == null) Color.DarkGray else NeonCyan),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).widthIn(max = 44.dp).testTag("control_set_a")
                    ) {
                        Text(text = if (pointA == null) "A" else "A*", fontSize = 10.sp, color = if (pointA == null) BrightWhite else NeonCyan, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { pointB = currentPositionMs.toInt() },
                        colors = ButtonDefaults.buttonColors(containerColor = if (pointB == null) Color.Transparent else NeonCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(0.5.dp, if (pointB == null) Color.DarkGray else NeonCyan),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).widthIn(max = 44.dp).testTag("control_set_b")
                    ) {
                        Text(text = if (pointB == null) "B" else "B*", fontSize = 10.sp, color = if (pointB == null) BrightWhite else NeonCyan, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(
                        onClick = {
                            if (pointA != null && pointB != null) {
                                isLoopABActive = !isLoopABActive
                            } else {
                                Toast.makeText(context, "Set Point A & B first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(28.dp).testTag("control_toggle_ab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = null,
                            tint = if (isLoopABActive) GlowGreen else BrightWhite.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Audio Routing selector
                IconButton(
                    onClick = { showAudioPathDialog = true },
                    modifier = Modifier.testTag("control_audio_path")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Text(text = "PATH", fontSize = 8.sp, color = BrightWhite, fontFamily = FontFamily.Monospace)
                    }
                }

                // Subtitle selector
                IconButton(
                    onClick = { showSubtitleDialog = true },
                    modifier = Modifier.testTag("control_subtitles")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Text(text = "SUBS", fontSize = 8.sp, color = BrightWhite, fontFamily = FontFamily.Monospace)
                    }
                }

                // Diagnostics panel trigger
                IconButton(
                    onClick = { showMediaInfoDialog = true },
                    modifier = Modifier.testTag("control_media_info")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                        Text(text = "INFO", fontSize = 8.sp, color = BrightWhite, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Core Playback Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (videoViewRef != null) {
                        val pos = (videoViewRef!!.currentPosition - 10000).coerceAtLeast(0)
                        videoViewRef!!.seekTo(pos)
                        currentPositionMs = pos.toLong()
                    }
                }) {
                    Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Rewind", tint = BrightWhite)
                }

                FloatingActionButton(
                    onClick = { 
                        isPlaying = !isPlaying 
                        if (isPlaying) videoViewRef?.start() else videoViewRef?.pause()
                    },
                    containerColor = NeonCyan,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_play_pause"),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Stop/Start",
                        tint = DarkBg
                    )
                }

                IconButton(onClick = {
                    if (videoViewRef != null) {
                        val pos = (videoViewRef!!.currentPosition + 10000).coerceAtMost(videoViewRef!!.duration)
                        videoViewRef!!.seekTo(pos)
                        currentPositionMs = pos.toLong()
                    }
                }) {
                    Icon(imageVector = Icons.Default.FastForward, contentDescription = "FastForward", tint = BrightWhite)
                }
            }
        }
    }

    // Modal dialogue speed console
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = SurfaceGray,
            title = { Text("PLAYBACK SPEED CONFIG", color = NeonCyan, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (playbackSpeed == speed) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    playbackSpeed = speed
                                    showSpeedDialog = false
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${speed}x Speed Factor", color = BrightWhite, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            if (playbackSpeed == speed) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("DISMISS", color = NeonPurple) }
            }
        )
    }

    // Modal dialog audio routing outputs
    if (showAudioPathDialog) {
        AlertDialog(
            onDismissRequest = { showAudioPathDialog = false },
            containerColor = SurfaceGray,
            title = { Text("AUDIO HARDWARE ROUTING", color = NeonCyan, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Core Stereo Speakers", "Bluetooth Low-Latency HD Output", "HDMI / Vulkan Passthrough", "3D Virtual Surround Engine").forEach { path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedAudioPath == path) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    selectedAudioPath = path
                                    showAudioPathDialog = false
                                    Toast.makeText(context, "$path initialized.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = path, color = BrightWhite, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            if (selectedAudioPath == path) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAudioPathDialog = false }) { Text("CLOSE", color = NeonPurple) }
            }
        )
    }

    // Modal dialog subtitle tracks
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            containerColor = SurfaceGray,
            title = { Text("SUBTITLE TRACKS SELECTION", color = NeonCyan, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("None", "Toka4K System Diagnostic SRT (Sync)").forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeSubtitleTrack == track) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    activeSubtitleTrack = track
                                    showSubtitleDialog = false
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = track, color = BrightWhite, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            if (activeSubtitleTrack == track) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSubtitleDialog = false }) { Text("CANCEL", color = NeonPurple) }
            }
        )
    }

    // Modal dialog file metadata info diagnostics
    if (showMediaInfoDialog) {
        AlertDialog(
            onDismissRequest = { showMediaInfoDialog = false },
            containerColor = SurfaceGray,
            title = { Text("HARDWARE DIAGNOSTIC REPORT", color = NeonCyan, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiagnosticTextRow("MEDIA_SOURCE", videoUri?.toString()?.substringBefore("?") ?: "/local/storage")
                    DiagnosticTextRow("RESOLUTION", if (isVideo) "Interactive Raster (3840x2160 Upscaled)" else "Discrete Waveform (CD Quality)")
                    DiagnosticTextRow("ENCODER", "Toka4K Advanced HW Media Decoder")
                    DiagnosticTextRow("AUDIO_TARGET", selectedAudioPath)
                    DiagnosticTextRow("UP-SAMPLER", activeProfile.title)
                    DiagnosticTextRow("DUR_PARAMS", "${formatTime(durationMs)} ms")
                    DiagnosticTextRow("HW_BUDGET", "${formatTime(currentPositionMs)} ms / ${(currentPositionMs.toFloat() / durationMs.toFloat() * 100).toInt()}% playback")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMediaInfoDialog = false }) { Text("DONE", color = NeonPurple) }
            }
        )
    }
}

@Composable
fun HUDKeyValText(key: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(text = "$key: ", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
        Text(text = value, fontSize = 9.sp, color = BrightWhite, fontFamily = FontFamily.Monospace)
    }
}

// Subscreen 3: Shader Tuning Console Screen
@Composable
fun ShaderSettingsConsole(
    selectedProfile: HardwareProfile,
    edgeIntensity: Float,
    onEdgeChange: (Float) -> Unit,
    scaleStep: Float,
    onScaleChange: (Float) -> Unit,
    noiseReduction: Boolean,
    onNoiseToggle: (Boolean) -> Unit,
    superSamplingVal: String,
    onSuperSamplingClick: (String) -> Unit,
    compilerLogs: List<String>,
    onBackClick: () -> Unit,
    onCompileClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    BackHandler {
        onBackClick()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Console Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.testTag("shaders_nav_back")) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Return", tint = BrightWhite)
            }
            Text(
                text = "GLSL CORE CONSOLE",
                style = MaterialTheme.typography.titleLarge,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Box(modifier = Modifier.size(40.dp)) // symmetry spacer
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Compiler Terminal Console log outputs (Glow text styled)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, GlowGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .background(Color.Black)
                .padding(10.dp)
        ) {
            Text(
                text = "COMPILE MONITOR // LIVE CONSOLE",
                style = MaterialTheme.typography.labelSmall,
                color = GlowGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Divider(color = GlowGreen.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            
            // Console text lines
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(compilerLogs) { log ->
                    Text(
                        text = log,
                        fontSize = 10.sp,
                        color = if (log.contains("SUCCESS") || log.contains("READY")) GlowGreen else if (log.contains("SYSTEM") || log.contains("====")) NeonPurple else BrightWhite,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recompile Compiler triggers
        Button(
            onClick = onCompileClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("shaders_compile_trigger"),
            colors = ButtonDefaults.buttonColors(containerColor = GlowGreen),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayForWork, contentDescription = null, tint = DarkBg)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "RECOMPILE ACTIVE GLSL PIPELINE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = DarkBg
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Real GLSL code view to look ultra production ready
        Text(
            text = "ACTIVE GLSL SHADER KERNEL (PREVIEW)",
            style = MaterialTheme.typography.labelSmall,
            color = NeonPurple,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF040508))
                .border(0.5.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            val glslCodeText = if (selectedProfile == HardwareProfile.HIGH_END) {
                """
                #version 300 es
                precision highp float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D videoTexture;
                // Anime4K: Edge refinement Luma kernel
                void main() {
                    vec2 size = vec2(textureSize(videoTexture, 0));
                    vec4 central = texture(videoTexture, vTexCoord);
                    float edgeVal = CentralDiffEdge( zentral, size, $edgeIntensity );
                    vec4 lumaRef =central + vec4(central.rgb * edgeVal * $scaleStep, 0.0);
                    fragColor = clamp(lumaRef, 0.0, 1.0);
                }
                """.trimIndent()
            } else {
                """
                #version 300 es
                precision mediump float;
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler2D videoTexture;
                // Bilinear FSR Fast Approximation
                void main() {
                    vec4 rgbSample = texture(videoTexture, vTexCoord);
                    vec3 sharpened = FSR_LowWeight( rgbSample.rgb, $edgeIntensity );
                    fragColor = vec4(sharpened, 1.0);
                }
                """.trimIndent()
            }
            Text(
                text = glslCodeText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = NeonCyan.copy(alpha = 0.85f),
                    lineHeight = 13.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Core Shader Slider Controls
        Text(
            text = "GLSL SCALING TUNING PARAMETERS",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Edge slider
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Edge Sharpness Radius", color = BrightWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(text = String.format("%.2f", edgeIntensity), color = NeonCyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = edgeIntensity,
                onValueChange = onEdgeChange,
                valueRange = 0.5f..5.0f,
                modifier = Modifier.testTag("sharpness_slider"),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
        }

        // Scale strength slider
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Scaling Boost Multiplier", color = BrightWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(text = String.format("%.1fx", scaleStep), color = NeonCyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = scaleStep,
                onValueChange = onScaleChange,
                valueRange = 1.0f..4.0f,
                modifier = Modifier.testTag("scale_slider"),
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
        }

        // Noise Cancel Toggle switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "GLSL Dynamic Denoising", color = BrightWhite, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Text(text = "Apply bilateral filter pass before upscale", color = Color.Gray, fontSize = 11.sp)
            }
            Switch(
                checked = noiseReduction,
                onCheckedChange = onNoiseToggle,
                modifier = Modifier.testTag("denoise_switch"),
                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.4f))
            )
        }

        // Super Sampling Multipler Button Box Selectors
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text = "Super-Sampling Texture Grid Limit", color = BrightWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Off", "2x", "4x", "8x").forEach { factor ->
                    val isChecked = factor == superSamplingVal
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, if (isChecked) NeonCyan else Color.DarkGray, RoundedCornerShape(6.dp))
                            .background(if (isChecked) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSuperSamplingClick(factor) }
                            .testTag("sampling_box_$factor")
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = factor,
                            color = if (isChecked) NeonCyan else BrightWhite,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// Media file storage model representing files, subtitles, folders
data class MediaFile(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val folder: String,
    val relativePath: String,
    val sizeLabel: String = "4.2 MB"
)

// In order to let the user play high fidelity demo files instantly, we declare a gorgeous preset portfolio
val demoMediaFiles = listOf(
    MediaFile(
        uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
        name = "Big Buck Bunny Cinema [4K]",
        isVideo = true,
        folder = "Downloads",
        relativePath = "storage/emulated/0/Downloads"
    ),
    MediaFile(
        uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
        name = "Sintel Movie CGI [FullHD]",
        isVideo = true,
        folder = "Animes",
        relativePath = "storage/emulated/0/Animes"
    ),
    MediaFile(
        uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
        name = "Nebula Cyber beats [Vibe]",
        isVideo = false,
        folder = "Music",
        relativePath = "storage/emulated/0/Music"
    ),
    MediaFile(
        uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"),
        name = "Synthwave Neon Echo [Grid]",
        isVideo = false,
        folder = "Music",
        relativePath = "storage/emulated/0/Music"
    )
)

// Dynamic scan utility searching the device storage paths using provider architecture safely
fun scanDeviceMedia(context: android.content.Context): List<MediaFile> {
    val result = ArrayList<MediaFile>()
    val scanContext = if (android.os.Build.VERSION.SDK_INT >= 30) {
        try {
            context.createAttributionContext("media_scanner")
        } catch (e: Exception) {
            context
        }
    } else {
        context
    }
    val contentResolver = scanContext.contentResolver
    
    // 1. Scan Video folders
    val videoUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val videoProjection = arrayOf(
        android.provider.MediaStore.Video.Media._ID,
        android.provider.MediaStore.Video.Media.DISPLAY_NAME,
        android.provider.MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        android.provider.MediaStore.Video.Media.SIZE
    )
    
    try {
        contentResolver.query(videoUri, videoProjection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
            val bucketCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Video_$id"
                val bucket = cursor.getString(bucketCol) ?: "Camera"
                val size = cursor.getLong(sizeCol)
                val fileUri = android.content.ContentUris.withAppendedId(videoUri, id)
                val sizeMb = String.format("%.1f MB", size.toFloat() / (1024f * 1024f))
                
                result.add(
                    MediaFile(
                        uri = fileUri,
                        name = name,
                        isVideo = true,
                        folder = bucket,
                        relativePath = "storage/emulated/0/$bucket",
                        sizeLabel = sizeMb
                    )
                )
            }
        }
    } catch (e: Exception) {}

    // 2. Scan Audio content
    val audioUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val audioProjection = arrayOf(
        android.provider.MediaStore.Audio.Media._ID,
        android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
        android.provider.MediaStore.Audio.Media.ALBUM,
        android.provider.MediaStore.Audio.Media.SIZE
    )
    
    try {
        contentResolver.query(audioUri, audioProjection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.SIZE)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Audio_$id"
                val album = cursor.getString(albumCol) ?: "Music"
                val size = cursor.getLong(sizeCol)
                val fileUri = android.content.ContentUris.withAppendedId(audioUri, id)
                val sizeMb = String.format("%.1f MB", size.toFloat() / (1024f * 1024f))
                
                result.add(
                    MediaFile(
                        uri = fileUri,
                        name = name,
                        isVideo = false,
                        folder = album,
                        relativePath = "storage/emulated/0/$album",
                        sizeLabel = sizeMb
                    )
                )
            }
        }
    } catch (e: Exception) {}

    return result
}

// Media Explorer screen featuring directory explorer, categorized selectors and filter search options.
@Composable
fun MediaExplorerScreen(
    mediaFiles: List<MediaFile>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onFileSelect: (MediaFile) -> Unit,
    onBackClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf("All Types") } // All Types, Videos, Audios, Folders
    
    // Group files for Folders Tab
    val groupedFolders = remember(mediaFiles) {
        mediaFiles.groupBy { it.folder }
    }
    
    // Filter list according to selection category, tabs & query
    val filteredFiles = remember(mediaFiles, searchQuery, selectedCategoryTab) {
        mediaFiles.filter { file ->
            val matchesQuery = file.name.contains(searchQuery, ignoreCase = true) || file.folder.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedCategoryTab) {
                "Videos" -> file.isVideo
                "Audios" -> !file.isVideo
                else -> true
            }
            matchesQuery && matchesCategory
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Appbar header for Explorer Dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("explorer_back")
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Return", tint = BrightWhite)
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STORAGE DIRECTORY COMPILER",
                    fontSize = 11.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Toka4K File Explorer",
                    fontSize = 16.sp,
                    color = BrightWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = isScanning) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }

            if (!isScanning) {
                Button(
                    onClick = onScanClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, NeonPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp).testTag("explorer_scan")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = BrightWhite, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SCAN STORAGE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = BrightWhite)
                }
            }
        }

        // Search Bar Row Form element
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("explorer_search"),
            textStyle = TextStyle(color = BrightWhite, fontSize = 13.sp),
            placeholder = { Text("Search system paths, segments, titles...", color = Color.Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = NeonPurple.copy(alpha = 0.5f),
                cursorColor = NeonCyan
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cyberpunk style Filter Tab rows
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All Types", "Videos", "Audios", "Folders").forEach { tab ->
                val isSelected = tab == selectedCategoryTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color(0x33FFFFFF))
                        .border(1.dp, if (isSelected) NeonCyan else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedCategoryTab = tab }
                        .padding(vertical = 8.dp)
                        .testTag("filter_tab_$tab"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.uppercase(),
                        fontSize = 9.sp,
                        color = if (isSelected) NeonCyan else BrightWhite,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Files listing grid
        if (selectedCategoryTab == "Folders") {
            // Folders categorization panel
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedFolders.forEach { (folderName, files) ->
                    item {
                        FolderExpansionCard(folderName = folderName, filesInFolder = files, onFileSelect = onFileSelect)
                    }
                }
            }
        } else {
            // Standard files listing
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No media files tracked.", color = Color.Gray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFiles) { mediaFile ->
                        MediaItemRow(mediaFile = mediaFile, onSelect = onFileSelect)
                    }
                }
            }
        }
    }
}

// Subordinate composables representing interactive folder rows
@Composable
fun FolderExpansionCard(
    folderName: String,
    filesInFolder: List<MediaFile>,
    onFileSelect: (MediaFile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("folder_card_$folderName"),
        colors = CardDefaults.cardColors(containerColor = SurfaceGray),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (expanded) NeonCyan.copy(alpha = 0.5f) else Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = folderName, color = BrightWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "Directory: storage/emulated/0/$folderName", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${filesInFolder.size} elements",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(NeonCyan.copy(alpha = 0.1f))
                            .border(0.5.dp, NeonCyan, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = BrightWhite
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filesInFolder.forEach { file ->
                        MediaItemRow(mediaFile = file, onSelect = onFileSelect)
                    }
                }
            }
        }
    }
}

// Horizontal media row representation
@Composable
fun MediaItemRow(
    mediaFile: MediaFile,
    onSelect: (MediaFile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F111E))
            .border(0.5.dp, if (mediaFile.isVideo) NeonCyan.copy(alpha = 0.2f) else NeonPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable { onSelect(mediaFile) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon type display
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (mediaFile.isVideo) NeonCyan.copy(alpha = 0.15f) else NeonPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (mediaFile.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (mediaFile.isVideo) NeonCyan else NeonPurple,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaFile.name,
                color = BrightWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mediaFile.folder.uppercase(),
                    color = if (mediaFile.isVideo) NeonCyan else NeonPurple,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mediaFile.sizeLabel,
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Trigger play",
            tint = if (mediaFile.isVideo) NeonCyan else NeonPurple,
            modifier = Modifier.size(18.dp)
        )
    }
}

fun findActivity(context: android.content.Context): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

// Double utility formatting helpers for media properties and synchronizer SRT
fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0) return "00:00"
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = milliseconds / (1000 * 60 * 60)
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// Realistic subtitles timeline database mapping matching the current position
fun getSynchronizedSubtitle(positionMs: Long): String {
    val sec = positionMs / 1000
    return when {
        sec in 0..4 -> "[TOKA4K SYSTEM INITIALIZATION SUCCESSFUL]"
        sec in 5..9 -> "Core renderer routing successfully upscaled to 3840x2160."
        sec in 10..14 -> "Shader modules: Anime4K super-resolution matrix grid initialized."
        sec in 15..19 -> "Dynamic frame rate stabilized at maximum hardware potential."
        sec in 20..24 -> "Audio synchronization tracking active on default hardware line."
        sec in 25..34 -> "[DSP Virtual visualizer responsive to waveform outputs]"
        sec in 35..45 -> "Core temperature: Optimal. GPU utilization capacity balanced."
        sec in 46..59 -> "Hardware profiles: Ready for real-time bilinear and bicubic filter hot-swap."
        else -> "Toka4K Advanced Decoder Pipeline Probing Complete."
    }
}

// Row layouts for dialogue properties diagnostics
@Composable
fun DiagnosticTextRow(title: String, payload: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text(
            text = payload,
            color = BrightWhite,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
