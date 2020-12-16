/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.app.fitserv.ui.videocall.model

import androidx.lifecycle.ViewModel
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.app.fitserv.ui.videocall.data.VideoAudioCollectionTile
import com.app.fitserv.ui.videocall.data.VideoCollectionTile

// This will be used for keeping state after rotation
class MeetingModel : ViewModel() {
    val currentVideoTiles = mutableMapOf<Int, VideoCollectionTile>()
    var currentMediaDevices :  MutableList<MediaDevice>? = null
    val currentVideoAudioTiles = mutableMapOf<String, VideoAudioCollectionTile>()
    var isMuted = false
    var isCameraOn = false
}
