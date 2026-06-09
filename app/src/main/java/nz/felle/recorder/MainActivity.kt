package nz.felle.recorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
	lateinit var saveDir: File

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		saveDir = File(filesDir, "recordings")
		try {
			saveDir.mkdir()
		} catch (_: Exception) {
			// Ignore
		}

		val ctx = this

		enableEdgeToEdge()
		setContent {
			Scaffold(
				modifier = Modifier.fillMaxSize(),
			) { innerPadding ->
				Box(
					modifier = Modifier
						.padding(innerPadding)
						.fillMaxSize(),
					contentAlignment = Alignment.BottomEnd,
				) {
					RecordingsList(ctx, saveDir)
					RecordingController(ctx, saveDir)
				}
			}
		}
		ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
	}
}

@Composable
private fun ElapsedIndicator(recording: ActiveRecording, modifier: Modifier) {
	val elapsed = produceState(Duration.ZERO) {
		while (isActive) {
			delay(1.seconds)
			try {
				value = recording.duration
			} catch (_: IllegalStateException) {
				// Recording ended.
				break
			}
		}
	}
	Text(elapsed.value.toHumanDuration(), modifier)
}

@Composable
private fun Visualizer(recording: ActiveRecording, modifier: Modifier) {
	val samples = produceState(listOf<Float>(), recording.recorder) {
		while (isActive) {
			delay(100.milliseconds)
			val amplitude = try {
				recording.recorder.maxAmplitude
			} catch (_: IllegalStateException) {
				// Recording ended.
				break
			}
			val sample = sqrt(amplitude/ 32768f)
			value = Stream.concat(Stream.of(sample), value.stream()).limit(256)
				.collect(Collectors.toList())
		}
	}
	val contentColor = LocalContentColor.current
	Spacer(
		modifier.drawBehind {
			val barWidth = 8f
			val barSpacing = 1f
			val barStride = barWidth + barSpacing
			var x = size.width - barStride
			for (sample in samples.value) {
				if (x < 0) {
					break
				}
				val barHeight = sample * (size.height - 4f) + 4f
				drawRoundRect(
					contentColor,
					Offset(x, (size.height - barHeight) * .5f),
					Size(barWidth, barHeight),
					CornerRadius(2f, 2f),
				)
				x -= barStride
			}
		},
	)
}

@Composable
private fun RecordingController(ctx: Activity, saveDir: File) {
	val BACKGROUND_COLOR = Color(0xFFF44336)
	val CONTENT_COLOR = Color(0xFFFFEBEE)

	val activeRecording = remember { mutableStateOf<ActiveRecording?>(null) }
	val isRecording = activeRecording.value != null

	DisposableEffect(Unit) {
		onDispose {
			activeRecording.value?.end()
		}
	}

	val inner = @Composable { isRecording: Boolean ->
		Row(verticalAlignment = Alignment.CenterVertically) {
			activeRecording.value?.let {
				ElapsedIndicator(it, Modifier.padding(24.dp, 0.dp, 16.dp, 0.dp))
				Visualizer(
					it,
					Modifier
						.weight(1f)
						.height(72.dp),
				)
			}
			TextButton(
				modifier = Modifier.padding(24.dp),
				onClick = {
					if (isRecording) {
						activeRecording.value?.end()
						activeRecording.value = null
					} else {
						try {
							activeRecording.value = ActiveRecording(ctx, saveDir)
						} catch (e: Exception) {
							Log.e("nz.felle.recorder", "error starting recording", e)
							val msg = if (e is MissingPermissionException) {
								ctx.getString(R.string.need_permission)
							} else {
								"${ctx.getString(R.string.error)}: ${e}"
							}
							Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
						}
					}
				},
			) {
				Icon(
					if (isRecording) {
						R.drawable.ic_stop
					} else {
						R.drawable.ic_mic
					},
					if (isRecording) {
						R.string.stop_recording
					} else {
						R.string.start_recording
					},
					tint = CONTENT_COLOR,
				)
			}
		}
	}

	AnimatedContent(isRecording) { isRecording ->
		Surface(
			color = BACKGROUND_COLOR,
			contentColor = CONTENT_COLOR,
			shape = RoundedCornerShape(16.dp),
			shadowElevation = 2.dp,
			modifier = Modifier.padding(16.dp),
		) {
			inner(isRecording)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsSheet(
	ctx: Context,
	file: File,
	onDismissSheet: () -> Unit,
) {
	val renameOpen = remember { mutableStateOf(false) }
	ModalBottomSheet(
		onDismissRequest = onDismissSheet,
	) {
		ListItem(
			headlineContent = { Text("Share") },
			leadingContent = { Icon(R.drawable.ic_share) },
			modifier = Modifier.clickable {
				val uri = FileProvider.getUriForFile(
					ctx,
					ctx.applicationContext.packageName + ".provider",
					file,
				)
				val sendIntent = Intent(Intent.ACTION_SEND)
				sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
				sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				sendIntent.putExtra(Intent.EXTRA_TITLE, file.name)
				sendIntent.type = ctx.applicationContext.contentResolver.getType(uri)
				val chooserIntent = Intent.createChooser(sendIntent, file.name)
				ctx.startActivity(chooserIntent)
				onDismissSheet()
			},
		)
		ListItem(
			headlineContent = { Text("Rename") },
			leadingContent = { Icon(R.drawable.ic_border_color) },
			modifier = Modifier.clickable {
				renameOpen.value = true
			},
		)
		ListItem(
			headlineContent = { Text("Delete") },
			leadingContent = { Icon(R.drawable.ic_delete) },
			modifier = Modifier.clickable {
				file.delete()
				onDismissSheet()
			},
		)
	}
	if (renameOpen.value) {
		val focusRequester = remember { FocusRequester() }

		val oldName = file.name
		val lastDotPos = oldName.lastIndexOf('.')
		val selection = if (lastDotPos == -1) {
			TextRange(oldName.length)
		} else {
			TextRange(0, lastDotPos)
		}
		val nameField = rememberTextFieldState(oldName, selection)
		val newFile = File(file.parentFile, nameField.text.toString())

		BasicAlertDialog({ renameOpen.value = false }) {
			Surface(
				modifier = Modifier.wrapContentSize(),
				shape = MaterialTheme.shapes.large,
				tonalElevation = AlertDialogDefaults.TonalElevation,
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(text = stringResource(R.string.rename))
					TextField(
						nameField,
						isError = nameField.text != oldName && newFile.exists(),
						modifier = Modifier.focusRequester(focusRequester),
					)
					Spacer(modifier = Modifier.height(24.dp))
					Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
						TextButton(onClick = onDismissSheet) {
							Text(stringResource(R.string.cancel))
						}
						TextButton(
							onClick = {
								file.renameTo(newFile)
								onDismissSheet()
							},
							enabled = nameField.text != oldName && !newFile.exists(),
						) {
							Text(stringResource(R.string.rename))
						}
					}
				}
			}
		}
		LaunchedEffect(focusRequester) {
			awaitFrame()
			focusRequester.requestFocus()
		}
	}
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
private fun RecordingsList(ctx: Context, dir: File) {
	val durationCache = remember { mutableStateMapOf<String, Long?>() }
	val recordings = liveDirectory(dir, { -it.createTime }, { durationCache.remove(it.name) })
	val chosenRecording = remember { mutableStateOf<File?>(null) }
	val scrollState = rememberScrollState()
	// Scroll to top when first item changes.
	LaunchedEffect(recordings.value.getOrNull(0)?.name) {
		scrollState.scrollTo(0)
	}

	PullToRefreshBox(
		isRefreshing = false,
		onRefresh = {
			durationCache.clear()
			recordings.forceUpdate()
		},
	) {
		if (recordings.value.isEmpty()) {
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Text(stringResource(R.string.start_recording))
			}
		} else {
			Column(
				Modifier
					.fillMaxSize()
					.verticalScroll(scrollState),
			) {
				recordings.value.forEach {
					key(it.name) {
						ListItem(
							headlineContent = {
								Text(it.name)
							},
							leadingContent = {
								Icon(R.drawable.ic_audio_file)
							},
							trailingContent = {
								durationCache.getOrPutIfMissing(it.name, fun() = it.getMediaDuration(ctx))
									?.let { dur ->
										val text = dur.milliseconds.toHumanDuration()
										Text(text)
									}
							},
							modifier = Modifier.combinedClickable(
								onClick = {
									val uri = FileProvider.getUriForFile(
										ctx,
										ctx.applicationContext.packageName + ".provider",
										it,
									)
									val intent = Intent(Intent.ACTION_VIEW, uri)
									intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
									ctx.startActivity(intent)
								},
								onLongClick = {
									chosenRecording.value = it
								},
							),
						)
					}
				}
			}
		}
	}

	chosenRecording.value?.let {
		ActionsSheet(ctx, it, onDismissSheet = { chosenRecording.value = null })
	}
}
