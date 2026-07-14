package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object UploadManager {
    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _speedString = MutableStateFlow("0 KB/s")
    val speedString = _speedString.asStateFlow()

    private val _uploadedSizeString = MutableStateFlow("0 MB")
    val uploadedSizeString = _uploadedSizeString.asStateFlow()

    private val _totalSizeString = MutableStateFlow("0 MB")
    val totalSizeString = _totalSizeString.asStateFlow()

    private val _remainingSizeString = MutableStateFlow("0 MB")
    val remainingSizeString = _remainingSizeString.asStateFlow()

    private val _etaString = MutableStateFlow("Calculating...")
    val etaString = _etaString.asStateFlow()

    private val _uploadTitle = MutableStateFlow("")
    val uploadTitle = _uploadTitle.asStateFlow()

    private val _uploadStatus = MutableStateFlow("")
    val uploadStatus = _uploadStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun updateProgress(
        isUploading: Boolean,
        progress: Float,
        speed: String,
        uploadedSize: String,
        totalSize: String,
        remainingSize: String,
        eta: String,
        title: String,
        status: String,
        errorMsg: String? = null
    ) {
        _isUploading.value = isUploading
        _progress.value = progress
        _speedString.value = speed
        _uploadedSizeString.value = uploadedSize
        _totalSizeString.value = totalSize
        _remainingSizeString.value = remainingSize
        _etaString.value = eta
        _uploadTitle.value = title
        _uploadStatus.value = status
        _error.value = errorMsg
    }
    
    fun reset() {
        _isUploading.value = false
        _progress.value = 0f
        _speedString.value = "0 KB/s"
        _uploadedSizeString.value = "0 MB"
        _totalSizeString.value = "0 MB"
        _remainingSizeString.value = "0 MB"
        _etaString.value = "Calculating..."
        _uploadTitle.value = ""
        _uploadStatus.value = ""
        _error.value = null
    }
}
