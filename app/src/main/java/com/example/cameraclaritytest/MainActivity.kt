package com.example.cameraclaritytest

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import com.example.cameraclaritytest.ui.theme.CameraClarityTestTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ExperimentalGetImage
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextField
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

class MainActivity : ComponentActivity() {
    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraClarityTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val context = LocalContext.current
                    var showCamera by remember { mutableStateOf(false) }
                    var qrCount by remember { mutableStateOf(0) } // 提升qrCount到Scaffold作用域
                    val cameraPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { granted ->
                            if (granted) {
                                showCamera = true
                            }
                        }
                    )
                    // 将isEditing提升到Scaffold作用域
                    val isEditing = remember { mutableStateOf(true) }
                    var resetFlag by remember { mutableStateOf(0) }
                    // 新增：已通过状态
                    var passed by remember { mutableStateOf(false) }
                    var updateFlag by remember { mutableStateOf(0) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(innerPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GreetingWithInput(
                                name = "MMA摄像头清晰度测试",
                                modifier = Modifier.padding(50.dp),
                                isEditing = isEditing,
                                showCamera = showCamera,
                                qrCount = qrCount,
                                resetFlag = resetFlag,
                                passed = passed,
                                onPassedChanged = { passed = it },
                                updateFlag = updateFlag
                            )
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!showCamera) {
                                Button(onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        showCamera = true
                                    }
                                    isEditing.value = false
                                }) {
                                    Text("打开前置摄像头")
                                }
                            }
                            if (showCamera) {
                                CameraPreview(onQrCountChanged = { count ->
                                    qrCount = count
                                    updateFlag++
                                })
                            }
                        }
                        // 页面底部重置按钮
                        if (passed) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(onClick = { resetFlag++; passed = false }) {
                                    Text("重置")
                                }
                            }
                        }
                        // 检测中..时底部提示文本
                        if (!passed && showCamera) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "当检测到二维码的个数大于等于设置的阈值并维持5s时，清晰度测试被视为通过！",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        // 页面底部版本号显示
                        val versionName = try {
                            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                            pInfo.versionName ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "版本号：$versionName",
                                color = Color(0xFF42A5F5),
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun GreetingWithInput(
    name: String,
    modifier: Modifier = Modifier,
    isEditing: MutableState<Boolean>,
    showCamera: Boolean,
    qrCount: Int,
    resetFlag: Int,
    passed: Boolean,
    onPassedChanged: (Boolean) -> Unit,
    updateFlag: Int
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var inputValue by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val saved = prefs.getInt("saved_int", 0)
        inputValue = if (saved == 0) "30" else saved.toString()
    }
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Box(modifier = Modifier.weight(0.5f, fill = false)) {
                TextField(
                    value = inputValue,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("\\d+"))) {
                            inputValue = newValue
                            val intValue = newValue.toIntOrNull() ?: 0
                            prefs.edit().putInt("saved_int", intValue).apply()
                        }
                    },
                    label = { Text("设置阈值", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    modifier = Modifier
                        .width(90.dp)
                        .height(55.dp),
                    enabled = isEditing.value,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number, // 文本类型
                        imeAction = ImeAction.Done    // 键盘动作
                    )
                )
            }
            Spacer(modifier = Modifier.width(200.dp))
            // 动态点动画
            var dotCount by remember { mutableStateOf(0) }
            // 计时器状态
            var thresholdReachedTime by remember(resetFlag) { mutableStateOf<Long?>(null) }
            LaunchedEffect(updateFlag) {
                val threshold = inputValue.toIntOrNull() ?: 0
                val now = System.currentTimeMillis()
                if (showCamera && !passed && threshold > 0) {
                    if (qrCount >= threshold) {
                        if (thresholdReachedTime == null) {
                            thresholdReachedTime = now
                        } else if (now - thresholdReachedTime!! >= 5000) {
                            onPassedChanged(true)
                        }
                    } else {
                        thresholdReachedTime = null
                    }
                } else {
                    thresholdReachedTime = null
                }
            }
            if (showCamera && !passed) {
                LaunchedEffect(showCamera) {
                    while (showCamera) {
                        dotCount = (dotCount + 1) % 4
                        delay(500)
                    }
                }
            } else {
                dotCount = 0
            }

            val statusText = when {
                passed -> "已通过"
                showCamera -> "检测中" + ".".repeat(dotCount)
                else -> "待开始"
            }
            val statusColor = when {
                passed -> Color(0xFF4CAF50) // 绿色
                showCamera -> Color(0xFFFFA500) // 棕黄色
                else -> Color.Gray
            }
            Text(
                text = statusText,
                fontSize = 25.sp,
                color = statusColor,
                modifier = Modifier.width(100.dp)
            )
        }
    }
}

@ExperimentalGetImage
@Composable
fun CameraPreview(onQrCountChanged: (Int) -> Unit) {
    val lastAnalysisTime = AtomicLong(0L)
    var qrCount by remember { mutableStateOf(0) }
    var updateFlag by remember { mutableStateOf(0) }
    LaunchedEffect(qrCount, updateFlag) {
        onQrCountChanged(qrCount)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(700.dp)
            .padding(start = 20.dp, end = 20.dp, top = 30.dp)
    ) {
        AndroidView(factory = { ctx ->
            val previewView = androidx.camera.view.PreviewView(ctx)
            previewView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                val barcodeScanner = BarcodeScanning.getClient()
                val imageAnalyzer = ImageAnalysis.Builder().build().apply {
                    setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastAnalysisTime.get() >= 500) {
                            lastAnalysisTime.set(currentTime)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                // 将 imageProxy 转为 Bitmap
                                val bitmap = imageProxyToBitmap(imageProxy)
                                val width = bitmap.width
                                val height = bitmap.height
                                val overlapRatio = 0.2f
                                val cellWidth = width / 4
                                val cellHeight = height / 6
                                val overlapX = (cellWidth * overlapRatio).toInt()
                                val overlapY = (cellHeight * overlapRatio).toInt()
                                val regions = mutableListOf<android.graphics.Rect>()
                                for (row in 0..5) {
                                    for (col in 0..3) {
                                        val left = (col * cellWidth - overlapX).coerceAtLeast(0)
                                        val top = (row * cellHeight - overlapY).coerceAtLeast(0)
                                        val right = ((col + 1) * cellWidth + overlapX).coerceAtMost(width)
                                        val bottom = ((row + 1) * cellHeight + overlapY).coerceAtMost(height)
                                        regions.add(android.graphics.Rect(left, top, right, bottom))
                                    }
                                }

                                regions.add(android.graphics.Rect(0, 0, width, height))

                                val allBarcodes = mutableSetOf<String>()
                                val tasks = regions.map { rect ->
                                    val regionBitmap = android.graphics.Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                                    val inputImage = InputImage.fromBitmap(regionBitmap, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(inputImage)
                                }
                                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                                    .addOnSuccessListener {
                                        tasks.forEach { task ->
                                            if (task.isSuccessful) {
                                                val barcodeTask = task // 无需强制类型转换
                                                val barcodes = barcodeTask.result ?: emptyList()
//                                                println("allBarcodes: ${barcodes.size}") // 打印所有非空二维码字符串
                                                barcodes.forEach { barcode ->
                                                    val rawValue = try {
                                                        val method = barcode.javaClass.getMethod("getRawValue")
                                                        method.invoke(barcode) as? String ?: ""
                                                    } catch (_: Exception) {
                                                        ""
                                                    }
                                                    allBarcodes.add(rawValue)
                                                }
                                            }
                                        }
                                        val filteredBarcodes = allBarcodes.filter { it.isNotEmpty() }
                                        qrCount = filteredBarcodes.size
                                        updateFlag++
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        ctx as ComponentActivity,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }, modifier = Modifier.fillMaxSize())

        Text(
            text = "检测到的二维码个数: $qrCount",
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                )
                .padding(5.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// 新增 imageProxy 转 Bitmap 工具方法
fun imageProxyToBitmap(imageProxy: androidx.camera.core.ImageProxy): android.graphics.Bitmap {
    val yBuffer = imageProxy.planes[0].buffer
    val uBuffer = imageProxy.planes[1].buffer
    val vBuffer = imageProxy.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}