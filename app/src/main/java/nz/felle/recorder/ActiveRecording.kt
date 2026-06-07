package nz.felle.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.nanoseconds

internal class MissingPermissionException : Exception("Missing permission to record audio")

internal class ActiveRecording(val recorder: MediaRecorder, val start: Long) {
	constructor(ctx: Context, saveDir: File) : this(MediaRecorder(ctx), System.nanoTime()) {
		if (ActivityCompat.checkSelfPermission(
				ctx,
				Manifest.permission.RECORD_AUDIO,
			) == PackageManager.PERMISSION_DENIED
		) {
			throw MissingPermissionException()
		}

		val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
		val fileName = "rec-${timestamp}.opus"
		val outFile = File(saveDir, fileName)

		recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
		recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
		recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
		recorder.setOutputFile(outFile)

		recorder.prepare()
		recorder.start()
	}

	val duration get() = (System.nanoTime() - start).nanoseconds

	fun end() {
		recorder.stop()
		recorder.release()
	}
}

