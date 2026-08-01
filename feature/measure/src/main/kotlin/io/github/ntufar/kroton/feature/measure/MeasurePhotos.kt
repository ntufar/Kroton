package io.github.ntufar.kroton.feature.measure

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.ntufar.kroton.model.PhotoPose
import io.github.ntufar.kroton.model.ProgressPhoto
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun PhotosSection(
    uiState: MeasureUiState,
    viewModel: MeasureViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Progress photos", fontWeight = FontWeight.Bold)
            PhotoCaptureButton(viewModel = viewModel)
        }
        PoseFilterRow(selected = uiState.poseFilter, onSelect = viewModel::setPoseFilter)
        val filtered = uiState.photos.filter { uiState.poseFilter == null || it.pose == uiState.poseFilter }
        PhotoGrid(photos = filtered, selectedIds = uiState.comparePhotoIds, onToggle = viewModel::toggleComparePhoto)
        if (uiState.comparePhotoIds.size == COMPARE_PHOTO_LIMIT) {
            CompareView(photos = uiState.photos.filter { it.id in uiState.comparePhotoIds })
        }
    }
}

@Composable
private fun PoseFilterRow(
    selected: PhotoPose?,
    onSelect: (PhotoPose?) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        PhotoPose.entries.forEach { pose ->
            Spacer(Modifier.width(4.dp))
            FilterChip(selected = selected == pose, onClick = { onSelect(pose) }, label = { Text(pose.name) })
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<ProgressPhoto>,
    selectedIds: List<Long>,
    onToggle: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(PHOTO_GRID_COLUMNS),
        modifier = Modifier.height(PHOTO_GRID_HEIGHT_DP.dp),
    ) {
        items(photos, key = {
            it.id
        }) { photo -> PhotoTile(photo = photo, isSelected = photo.id in selectedIds, onClick = { onToggle(photo.id) }) }
    }
}

@Composable
private fun PhotoTile(
    photo: ProgressPhoto,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.size(PHOTO_TILE_SIZE_DP.dp).padding(2.dp),
    ) {
        val bitmap =
            remember(photo.fileName) {
                runCatching {
                    BitmapFactory.decodeFile(
                        File(context.filesDir, "photos/${photo.fileName}").path,
                    )
                }.getOrNull()
            }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.pose.name,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onClick) { Text(if (isSelected) "✓" else photo.pose.name.take(1)) }
    }
}

@Composable
private fun CompareView(photos: List<ProgressPhoto>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        photos.forEach { photo -> Text(photo.pose.name, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun PhotoCaptureButton(viewModel: MeasureViewModel) {
    val context = LocalContext.current
    var pendingFile: File? by remember { mutableStateOf(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                val nowMs = System.currentTimeMillis()
                val localDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
                viewModel.addPhoto(file.name, PhotoPose.FRONT, nowMs, localDate)
            }
        }
    TextButton(
        onClick = {
            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(photosDir, "progress_${System.currentTimeMillis()}.jpg")
            pendingFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launcher.launch(uri)
        },
    ) { Text("Add photo") }
}

private const val PHOTO_GRID_COLUMNS = 3
private const val PHOTO_GRID_HEIGHT_DP = 240
private const val PHOTO_TILE_SIZE_DP = 100
private const val COMPARE_PHOTO_LIMIT = 2
