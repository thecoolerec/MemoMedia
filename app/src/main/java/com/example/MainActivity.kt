package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.categories.CategoriesScreen
import com.example.ui.categories.CategoriesViewModel
import com.example.ui.inbox.InboxScreen
import com.example.ui.inbox.InboxViewModel
import com.example.ui.navigation.AppBottomBar
import com.example.ui.navigation.Destination
import com.example.ui.photos.PhotosScreen
import com.example.ui.photos.PhotosViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LocalMediaTheme
import com.example.watcher.MediaJobService
import com.example.watcher.MediaMonitorService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocalMediaTheme {
                MainAppScreen()
            }
        }
    }
}

sealed interface MediaAccessState {
    object Full : MediaAccessState
    object Partial : MediaAccessState
    object None : MediaAccessState
}

fun checkMediaAccessState(context: android.content.Context): MediaAccessState {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val hasImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val hasSelected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        
        if (hasImages && hasVideo) return MediaAccessState.Full
        if (hasSelected) return MediaAccessState.Partial
        return MediaAccessState.None
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        if (hasImages && hasVideo) return MediaAccessState.Full
        return MediaAccessState.None
    } else {
        val hasStorage = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (hasStorage) return MediaAccessState.Full
        return MediaAccessState.None
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LocalMediaApplication

    val permissionsToRequest = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list.toTypedArray()
    }

    var accessState by remember {
        mutableStateOf(checkMediaAccessState(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val newState = checkMediaAccessState(context)
        accessState = newState
        if (newState != MediaAccessState.None) {
            app.monitoringController.applyCurrentState()
        }
    }

    LaunchedEffect(Unit) {
        if (accessState == MediaAccessState.None) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            app.monitoringController.applyCurrentState()
        }
    }

    if (accessState == MediaAccessState.None) {
        PermissionRequiredScreen(
            onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) }
        )
        return
    }

    val navController = rememberNavController()

    // Use the same database definition as the Inbox so its badge can never
    // disagree with the content shown after navigation.
    val pendingCount by app.mediaRepository.observePendingCount().collectAsStateWithLifecycle(0)

    Scaffold(
        bottomBar = {
            AppBottomBar(
                navController = navController,
                pendingCount = pendingCount,
                expiredCount = 0
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (accessState == MediaAccessState.Partial) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "当前为部分照片授权，仅能管理选中的媒体",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { permissionLauncher.launch(permissionsToRequest) }
                        ) {
                            Text("授予全部")
                        }
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = Destination.Photos.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(Destination.Photos.route) {
                    val viewModel: PhotosViewModel = viewModel()
                    PhotosScreen(viewModel = viewModel)
                }

                composable(Destination.Inbox.route) {
                    val viewModel: InboxViewModel = viewModel()
                    InboxScreen(viewModel = viewModel)
                }

                composable(Destination.Categories.route) {
                    val viewModel: CategoriesViewModel = viewModel()
                    CategoriesScreen(viewModel = viewModel)
                }

                composable(Destination.Settings.route) {
                    val viewModel: SettingsViewModel = viewModel()
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "需要媒体访问权限",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本应用为纯本地整理器，需要读取您设备上的照片和视频以便进行聚合、分类与生命周期管理。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.testTag("request_permission_button")
            ) {
                Text("授予媒体权限")
            }
        }
    }
}

