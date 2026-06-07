package nz.felle.recorder

import android.os.FileObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import java.io.File

internal data class LiveDirectory(val contents: State<Array<File>>, val forceUpdate: () -> Unit) {
	val value get() = contents.value
}

@Composable
internal fun <K : Comparable<K>> liveDirectory(
	dir: File,
	sortBy: ((File) -> K)? = null,
	fileChanged: (File) -> Unit,
): LiveDirectory {
	val makeValue = fun(): Array<File> {
		val ret = dir.listFiles()!!
		if (sortBy != null) {
			ret.sortBy(sortBy)
		}
		return ret
	}

	val forceUpdate = remember { mutableStateOf<(() -> Unit)?>(null) }
	val contents = produceState(makeValue()) {
		val observer = object : FileObserver(dir, CREATE or DELETE or MOVED_TO or CLOSE_WRITE) {
			override fun onEvent(action: Int, path: String?) {
				if (action == CLOSE_WRITE) {
					path?.let { fileChanged(File(dir, it)) }
				} else {
					value = makeValue()
				}
			}
		}
		forceUpdate.value = fun() {
			value = makeValue()
		}
		observer.startWatching()

		// Suspend point is here; everything up to here was executed immediately.
		awaitDispose {
			observer.stopWatching()
		}
	}
	return LiveDirectory(
		contents,
		fun() {
			forceUpdate.value?.invoke()
		},
	)
}

