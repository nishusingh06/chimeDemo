/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.app.fitserv.ui.videocall.data

import com.amazonaws.services.chime.sdk.meetings.audiovideo.SignalStrength
import com.amazonaws.services.chime.sdk.meetings.audiovideo.VolumeLevel
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState

data class VideoAudioCollectionTile(
    val attendeeId: String,
    val attendeeName: String,
    var volumeLevel: VolumeLevel,
    val signalStrength: SignalStrength = SignalStrength.High,
    val isActiveSpeaker: Boolean = false,
    var videoTileState: VideoTileState?,
    var isMuted: Boolean,
    var isTileRemoved: Boolean,
    var isTilePaused: Boolean = false,
    var isTileAdded: Boolean = false
)