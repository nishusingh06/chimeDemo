/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.app.fitserv.ui.videocall.data

import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState

data class VideoCollectionTile(
        val attendeeName: String,
        val videoTileState: VideoTileState,
        var isMuted: Boolean,
        var isTileRemoved: Boolean = true
)
