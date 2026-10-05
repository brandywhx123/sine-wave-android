package com.example.sinegenerator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sinegenerator.ui.theme.SineGeneratorTheme
import kotlin.math.PI
import kotlin.math.sin

private const val FREQ_MIN = 20
private const val FREQ_MAX = 20_000
private const val DEFAULT_FREQ = 440

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SineGeneratorTheme {
                SineGeneratorScreen()
            }
        }
    }
}

@Composable
private fun SineGeneratorScreen() {
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val player = remember { SineWavePlayer() }

    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectedFrequency by remember { mutableStateOf<Double?>(null) }

    val detector = remember { FrequencyDetector { freq -> detectedFrequency = freq } }

    var frequency by rememberSaveable { mutableStateOf(DEFAULT_FREQ) }
    var freqInput by rememberSaveable { mutableStateOf(DEFAULT_FREQ.toString()) }
    var step by rememberSaveable { mutableStateOf(1) }
    var fieldFocused by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (detector.start()) isDetecting = true
        }
    }

    fun toggleDetection() {
        if (isDetecting) {
            detector.stop()
            isDetecting = false
        } else {
            val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                if (detector.start()) isDetecting = true
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // 频率变化实时下发，播放中同样生效
    LaunchedEffect(frequency) {
        player.frequency = frequency.toDouble()
    }

    // 离开界面（切后台）时自动停止发声和检测
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (player.isPlaying) {
                    player.stop()
                    isPlaying = false
                }
                if (detector.isRunning) {
                    detector.stop()
                    isDetecting = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
            detector.stop()
        }
    }

    fun commitFrequency() {
        val parsed = freqInput.trim().toIntOrNull()
        if (parsed == null) {
            freqInput = frequency.toString()
            return
        }
        frequency = parsed.coerceIn(FREQ_MIN, FREQ_MAX)
        freqInput = frequency.toString()
    }

    fun changeFrequency(delta: Int) {
        val base = freqInput.toIntOrNull() ?: frequency
        frequency = (base + delta).coerceIn(FREQ_MIN, FREQ_MAX)
        freqInput = frequency.toString()
    }

    fun togglePlayback() {
        if (isPlaying) {
            player.stop()
            isPlaying = false
        } else {
            commitFrequency()
            player.frequency = frequency.toDouble()
            player.start()
            isPlaying = true
            focusManager.clearFocus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "正弦波发生器",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            Text(
                text = "频率（Hz）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdjustButton(
                    symbol = "−",
                    enabled = frequency > FREQ_MIN,
                    onClick = { changeFrequency(-step) }
                )

                FrequencyField(
                    value = freqInput,
                    focused = fieldFocused,
                    onValueChange = { newValue ->
                        // 仅接受 5 位以内纯数字
                        if (newValue.length <= 5 && newValue.all(Char::isDigit)) {
                            freqInput = newValue
                        }
                    },
                    onFocusChanged = { focused ->
                        if (fieldFocused && !focused) commitFrequency()
                        fieldFocused = focused
                    },
                    onDone = {
                        commitFrequency()
                        focusManager.clearFocus()
                    }
                )

                AdjustButton(
                    symbol = "+",
                    enabled = frequency < FREQ_MAX,
                    onClick = { changeFrequency(step) }
                )
            }

            Spacer(Modifier.height(18.dp))

            StepSelector(selected = step, onSelect = { step = it })

            Spacer(Modifier.height(32.dp))

            WaveformIndicator(playing = isPlaying)

            Spacer(Modifier.height(32.dp))

            PlayStopButton(playing = isPlaying, onClick = ::togglePlayback)

            Spacer(Modifier.height(16.dp))

            DetectButton(detecting = isDetecting, onClick = ::toggleDetection)

            Spacer(Modifier.height(12.dp))

            DetectedFrequencyDisplay(
                frequency = detectedFrequency
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "可调范围 $FREQ_MIN – $FREQ_MAX Hz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 圆形 − / + 调节按钮 */
@Composable
private fun AdjustButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier.size(60.dp)
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

/** 中间的频率数值输入框：点击弹出数字键盘直接输入 */
@Composable
private fun FrequencyField(
    value: String,
    focused: Boolean,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    val lineColor by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "fieldLine"
    )

    Box(
        modifier = Modifier
            .width(170.dp)
            .height(72.dp)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .drawBehind {
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            },
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.displaySmall.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 步进选择：1Hz / 10Hz / 100Hz */
@Composable
private fun StepSelector(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 10, 100).forEach { value ->
            val active = value == selected
            val container by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                label = "stepChip"
            )
            Surface(
                onClick = { onSelect(value) },
                shape = CircleShape,
                color = container,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) {
                    Text(
                        text = "${value}Hz",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/** 播放状态波形动画：播放时正弦波横向流动，停止时收为直线 */
@Composable
private fun WaveformIndicator(playing: Boolean) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
                )
                progress.snapTo(0f)
            }
        }
    }

    val amplitude by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "waveAmplitude"
    )

    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val path = Path()
        val midY = size.height / 2f
        val segments = 120
        val cycles = 3f

        for (i in 0..segments) {
            val x = size.width * i / segments
            val theta = (i.toFloat() / segments * cycles + progress.value) *
                (2f * PI).toFloat()
            val y = midY + sin(theta) * (size.height * 0.40f) * amplitude
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

/** 播放 / 停止 切换按钮：播放时变红表示正在输出 */
@Composable
private fun PlayStopButton(
    playing: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (playing) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 200),
        label = "playButton"
    )

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        if (playing) {
            // 停止图标：白色圆角方块
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }
            )
        } else {
            // 播放图标：自绘白色三角形
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .drawBehind {
                        val triangle = Path().apply {
                            moveTo(size.width * 0.10f, size.height * 0.08f)
                            lineTo(size.width * 0.10f, size.height * 0.92f)
                            lineTo(size.width * 0.92f, size.height * 0.5f)
                            close()
                        }
                        drawPath(path = triangle, color = Color.White)
                    }
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = if (playing) "停止" else "播放",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** 频率检测 / 停止检测 切换按钮 */
@Composable
private fun DetectButton(
    detecting: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (detecting) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = tween(durationMillis = 200),
        label = "detectButton"
    )

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(
            text = if (detecting) "停止检测" else "检测声音频率",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

/** 检测到的频率数值显示：检测中实时显示，停止后保留最后一次结果 */
@Composable
private fun DetectedFrequencyDisplay(
    frequency: Double?
) {
    val text = when {
        frequency == null -> "未检测"
        frequency < 0 -> "—"
        else -> String.format("%.1f Hz", frequency)
    }

    Text(
        text = "检测频率：$text",
        style = MaterialTheme.typography.bodyMedium,
        color = if (frequency != null && frequency >= 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}
