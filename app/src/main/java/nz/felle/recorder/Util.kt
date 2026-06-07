package nz.felle.recorder

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import kotlin.time.Duration

internal val File.createTime: Long
	get() = Files.readAttributes(this.toPath(), BasicFileAttributes::class.java).creationTime()
		.toMillis()

internal fun File.getMediaDuration(ctx: Context): Long? {
	val retriever = MediaMetadataRetriever()
	return try {
		val uri = FileProvider.getUriForFile(
			ctx,
			ctx.applicationContext.packageName + ".provider",
			this,
		)
		retriever.setDataSource(ctx, uri)
		val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
		retriever.release()
		duration?.toLongOrNull()
	} catch (_: Exception) {
		null
	}
}

internal fun Duration.toHumanDuration() =
	this.toComponents { min, sec, _ -> "%02d:%02d".format(min, sec) }

@Composable
internal fun Icon(id: Int, description: Int? = null, tint: Color = LocalContentColor.current) {
	androidx.compose.material3.Icon(
		ImageVector.vectorResource(id),
		contentDescription = description?.let { stringResource(it) },
		tint = tint,
	)
}