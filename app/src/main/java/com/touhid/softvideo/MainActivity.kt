package com.touhid.softvideo

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Red = Color(0xFFE53935)
private val Bg = Color(0xFF121212)

class MainActivity : ComponentActivity() {
    lateinit var player: ExoPlayer
    val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    var queue by mutableStateOf<List<Video>>(emptyList())
    var playerOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        player = ExoPlayer.Builder(this).build()
        setContent { SoftTheme { App(this) } }
    }

    fun startPlayback(list: List<Video>, index: Int) {
        queue = list
        val v = list[index]
        val saved = prefs.getLong("pos_${v.id}", 0L)
        val start = if (saved > 0 && saved < v.durationMs - 5000) saved else 0L
        player.setMediaItems(list.map { MediaItem.fromUri(it.uri) }, index, start)
        player.setPlaybackSpeed(1f)
        player.prepare()
        player.playWhenReady = true
        playerOpen = true
    }

    fun closePlayer() {
        savePosition()
        player.pause()
        player.clearMediaItems()
        playerOpen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun savePosition() {
        val v = queue.getOrNull(player.currentMediaItemIndex) ?: return
        val pos = player.currentPosition
        val e = prefs.edit()
        if (pos > 5000 && pos < v.durationMs - 5000) e.putLong("pos_${v.id}", pos)
        else e.remove("pos_${v.id}")
        e.apply()
    }

    override fun onStop() {
        super.onStop()
        if (playerOpen) {
            savePosition()
            player.pause()
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
fun SoftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Red,
            background = Bg,
            surface = Bg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) { content() }
    }
}

@Composable
fun App(activity: MainActivity) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }

    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, reload) {
        if (granted) videos = withContext(Dispatchers.IO) { loadVideos(ctx) }
    }

    var tab by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<String?>(null) }

    // ---- file actions (delete / rename / share) ----
    // Android asks the user "Allow Soft Video to ..." first; afterGrant runs only if they tap Allow
    val scope = rememberCoroutineScope()
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    var renameTarget by remember { mutableStateOf<Video?>(null) }
    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) afterGrant?.invoke()
        afterGrant = null
    }

    fun askThen(pi: PendingIntent, then: () -> Unit) {
        afterGrant = then
        grantLauncher.launch(IntentSenderRequest.Builder(pi).build())
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun delete(v: Video) {
        askThen(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(v.uri))) {
            videos = videos.filter { it.id != v.id }
            activity.prefs.edit().remove("pos_${v.id}").apply()
            toast("Deleted")
        }
    }

    fun rename(v: Video, title: String) {
        askThen(MediaStore.createWriteRequest(ctx.contentResolver, listOf(v.uri))) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { renameVideo(ctx, v, title) }
                if (f != null) {
                    videos = videos.map { if (it.id == v.id) it.copy(title = title, file = f) else it }
                    toast("Renamed")
                } else {
                    toast("Rename failed (a file with that name may already exist)")
                }
            }
        }
    }

    fun fileActions(v: Video) = listOf<Pair<String, () -> Unit>>(
        "Rename" to { renameTarget = v },
        "Share" to { shareVideo(ctx, v) },
        "Delete from device" to { delete(v) }
    )

    renameTarget?.let { v ->
        var name by remember(v.id) { mutableStateOf(v.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty() && n != v.title) rename(v, n)
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    BackHandler(enabled = activity.playerOpen) { activity.closePlayer() }
    BackHandler(enabled = !activity.playerOpen && tab == 1 && folder != null) { folder = null }

    if (activity.playerOpen) {
        PlayerScreen(activity)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Soft Video",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Red) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Videos") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Folders") })
        }

        if (!granted) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Allow access to your videos to see them here")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch(perm) }) { Text("Allow") }
            }
        } else if (tab == 0) {
            VideoList(videos, { fileActions(it) }) { i -> activity.startPlayback(videos, i) }
        } else {
            val current = folder
            if (current == null) {
                val groups = videos.groupBy { it.folder }.toList()
                    .sortedBy { it.first.lowercase() }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(groups, key = { _, g -> g.first }) { _, g ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { folder = g.first }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = Red, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(g.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${g.second.size} videos",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                val list = videos.filter { it.folder == current }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { folder = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Text(current, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                VideoList(list, { fileActions(it) }) { i -> activity.startPlayback(list, i) }
            }
        }
    }
}

@Composable
fun VideoList(
    list: List<Video>,
    actions: (Video) -> List<Pair<String, () -> Unit>>,
    onPlay: (Int) -> Unit
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No videos found", color = Color.Gray)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, v -> v.id }) { i, v ->
            var menuOpen by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(i) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(120.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                ) {
                    Thumb(v)
                    Text(
                        formatTime(v.durationMs),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(v.folder, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, "More", tint = Color.Gray)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        actions(v).forEach { (label, fn) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    menuOpen = false
                                    fn()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Thumb(v: Video) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, v.id) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(v.uri, Size(320, 180), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    bmp?.let {
        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(activity: MainActivity) {
    val exo = activity.player
    val queue = activity.queue

    var index by remember { mutableIntStateOf(exo.currentMediaItemIndex) }
    var isPlaying by remember { mutableStateOf(exo.isPlaying) }
    var position by remember { mutableLongStateOf(exo.currentPosition) }
    var duration by remember {
        mutableLongStateOf(queue.getOrNull(exo.currentMediaItemIndex)?.durationMs ?: 0L)
    }
    var speed by remember { mutableFloatStateOf(1f) }
    var showControls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // Hide system bars while the player is open
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                index = exo.currentMediaItemIndex
                duration = queue.getOrNull(index)?.durationMs ?: 0L
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    activity.requestedOrientation =
                        if (size.width >= size.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(250)
            position = exo.currentPosition
            val d = exo.duration
            if (d > 0) duration = d
            n++
            if (n % 8 == 0) activity.savePosition()
        }
    }

    LaunchedEffect(showControls, isPlaying, touch) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    val title = queue.getOrNull(index)?.title ?: ""

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        touch++
                    },
                    onDoubleTap = { offset ->
                        val delta = if (offset.x < size.width / 2) -10_000L else 10_000L
                        val max = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
                        exo.seekTo((exo.currentPosition + delta).coerceIn(0L, max))
                    }
                )
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    player = exo
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activity.closePlayer() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val land = activity.resources.configuration.orientation ==
                                Configuration.ORIENTATION_LANDSCAPE
                        activity.requestedOrientation =
                            if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        touch++
                    }) {
                        Icon(Icons.Filled.ScreenRotation, "Rotate", tint = Color.White)
                    }
                }

                // Centre controls
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = { exo.seekToPreviousMediaItem(); touch++ }) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = {
                        exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L)); touch++
                    }) {
                        Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(
                        onClick = {
                            if (exo.playbackState == Player.STATE_ENDED) {
                                exo.seekTo(0L)
                                exo.play()
                            } else if (exo.isPlaying) exo.pause() else exo.play()
                            touch++
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Play or pause",
                            tint = Red,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = {
                        val max = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
                        exo.seekTo((exo.currentPosition + 10_000L).coerceAtMost(max)); touch++
                    }) {
                        Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { exo.seekToNextMediaItem(); touch++ }) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Bottom bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(if (dragging) dragValue.toLong() else position),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = (if (dragging) dragValue else position.toFloat())
                            .coerceIn(0f, maxOf(duration, 1L).toFloat()),
                        onValueChange = {
                            dragging = true
                            dragValue = it
                            touch++
                        },
                        onValueChangeFinished = {
                            exo.seekTo(dragValue.toLong())
                            dragging = false
                        },
                        valueRange = 0f..maxOf(duration, 1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Red,
                            activeTrackColor = Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x",
                        color = Red,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f)
                                val next = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
                                speed = next
                                exo.setPlaybackSpeed(next)
                                touch++
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
