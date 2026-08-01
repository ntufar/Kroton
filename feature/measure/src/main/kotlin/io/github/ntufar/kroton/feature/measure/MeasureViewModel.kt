package io.github.ntufar.kroton.feature.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ntufar.kroton.domain.DerivedMetrics
import io.github.ntufar.kroton.domain.MeasurementRepository
import io.github.ntufar.kroton.domain.MeasurementSummary
import io.github.ntufar.kroton.domain.ProfileRepository
import io.github.ntufar.kroton.model.MeasurementEntry
import io.github.ntufar.kroton.model.MeasurementType
import io.github.ntufar.kroton.model.PhotoPose
import io.github.ntufar.kroton.model.ProgressPhoto
import io.github.ntufar.kroton.model.Sex
import io.github.ntufar.kroton.model.UnitKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeasureUiState(
    val isLoading: Boolean = true,
    val enabledTypes: List<MeasurementType> = emptyList(),
    val allTypes: List<MeasurementType> = emptyList(),
    val summaries: Map<Long, MeasurementSummary> = emptyMap(),
    val derived: DerivedMetrics? = null,
    val needsProfileSetup: Boolean = false,
    val photos: List<ProgressPhoto> = emptyList(),
    val poseFilter: PhotoPose? = null,
    val comparePhotoIds: List<Long> = emptyList(),
    val selectedTypeId: Long? = null,
    val entriesForSelectedType: List<MeasurementEntry> = emptyList(),
    val isManageTypesOpen: Boolean = false,
)

@Suppress("TooManyFunctions")
class MeasureViewModel(
    private val repository: MeasurementRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeEnabledTypes(), repository.observeAllTypes()) { enabled, all -> enabled to all }
                .collect { (enabled, all) ->
                    val summaries = enabled.associate { it.id to repository.summary(it) }
                    _uiState.update {
                        it.copy(isLoading = false, enabledTypes = enabled, allTypes = all, summaries = summaries)
                    }
                }
        }
        viewModelScope.launch {
            repository.observePhotos().collect { photos -> _uiState.update { it.copy(photos = photos) } }
        }
        refreshDerivedMetrics()
    }

    private fun refreshDerivedMetrics() {
        viewModelScope.launch {
            val derived = repository.derivedMetrics(System.currentTimeMillis())
            val profile = profileRepository.get()
            _uiState.update {
                it.copy(derived = derived, needsProfileSetup = profile?.heightCm == null || profile.sex == null)
            }
        }
    }

    fun quickAdd(
        typeId: Long,
        value: Double,
        nowMs: Long,
        localDate: Int,
    ) {
        viewModelScope.launch {
            repository.addOrReplaceEntry(typeId, value, nowMs, localDate)
            refreshSummaries()
            refreshDerivedMetrics()
        }
    }

    private suspend fun refreshSummaries() {
        val summaries = _uiState.value.enabledTypes.associate { it.id to repository.summary(it) }
        _uiState.update { it.copy(summaries = summaries) }
    }

    fun openTypeHistory(typeId: Long) {
        viewModelScope.launch {
            val entries = repository.observeEntries(typeId).first()
            _uiState.update { it.copy(selectedTypeId = typeId, entriesForSelectedType = entries) }
        }
    }

    fun closeTypeHistory() = _uiState.update { it.copy(selectedTypeId = null, entriesForSelectedType = emptyList()) }

    fun addBackdatedEntry(
        typeId: Long,
        value: Double,
        recordedAt: Long,
        localDate: Int,
    ) {
        viewModelScope.launch {
            repository.addOrReplaceEntry(typeId, value, recordedAt, localDate)
            openTypeHistory(typeId)
            refreshSummaries()
        }
    }

    fun updateEntry(
        entryId: Long,
        value: Double,
    ) {
        val typeId = _uiState.value.selectedTypeId ?: return
        viewModelScope.launch {
            repository.updateEntry(entryId, value, note = null)
            openTypeHistory(typeId)
            refreshSummaries()
        }
    }

    fun deleteEntry(entryId: Long) {
        val typeId = _uiState.value.selectedTypeId ?: return
        viewModelScope.launch {
            repository.deleteEntry(entryId)
            openTypeHistory(typeId)
            refreshSummaries()
        }
    }

    fun setManageTypesOpen(open: Boolean) = _uiState.update { it.copy(isManageTypesOpen = open) }

    fun createCustomType(
        name: String,
        unitKind: UnitKind,
    ) {
        viewModelScope.launch {
            repository.createCustomType(
                name,
                unitKind,
                decimals = 1,
                nowMs = System.currentTimeMillis(),
            )
        }
    }

    fun setTypeEnabled(
        typeId: Long,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            repository.setTypeEnabled(typeId, enabled)
            refreshSummaries()
        }
    }

    fun setProfileHeightAndSex(
        heightCm: Double,
        sex: Sex,
    ) {
        viewModelScope.launch {
            profileRepository.updateHeightAndSex(heightCm, sex)
            refreshDerivedMetrics()
        }
    }

    fun addPhoto(
        fileName: String,
        pose: PhotoPose,
        nowMs: Long,
        localDate: Int,
    ) {
        viewModelScope.launch {
            repository.addPhoto(
                fileName,
                pose,
                note = null,
                recordedAt = nowMs,
                localDate = localDate,
            )
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch { repository.deletePhoto(photoId) }
        _uiState.update { it.copy(comparePhotoIds = it.comparePhotoIds - photoId) }
    }

    fun setPoseFilter(pose: PhotoPose?) = _uiState.update { it.copy(poseFilter = pose) }

    fun toggleComparePhoto(photoId: Long) =
        _uiState.update {
            val current = it.comparePhotoIds
            val next =
                when {
                    photoId in current -> current - photoId
                    current.size < COMPARE_PHOTO_LIMIT -> current + photoId
                    else -> listOf(current.last(), photoId)
                }
            it.copy(comparePhotoIds = next)
        }

    private companion object {
        const val COMPARE_PHOTO_LIMIT = 2
    }
}
