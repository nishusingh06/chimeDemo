package com.app.fitserv.ui.videocall.adapter

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AudioVideoFacade
import com.amazonaws.services.chime.sdk.meetings.audiovideo.SignalStrength
import com.amazonaws.services.chime.sdk.meetings.audiovideo.VolumeLevel
import com.app.fitserv.ui.videocall.data.VideoAudioCollectionTile
import com.example.chimevideocalldemo.R
import com.example.chimevideocalldemo.utils.AppUtil
import com.example.chimevideocalldemo.utils.AppUtil.Companion.inflate
import kotlinx.android.synthetic.main.item_video_adapter.view.*

class VideoAdapter(
    private val videoCollectionTiles: Collection<VideoAudioCollectionTile>,
    private val audioVideoFacade: AudioVideoFacade,
    private val context: Context
) :
    RecyclerView.Adapter<VideoAdapter.VideoHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoHolder {
        val inflatedView = parent.inflate(R.layout.item_video_adapter, false)
        return VideoHolder(inflatedView, audioVideoFacade)
    }

    override fun getItemCount(): Int {
        return videoCollectionTiles.size
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int) {
        val videoCollectionTile = videoCollectionTiles.elementAt(position)
        holder.bindVideoTile(videoCollectionTile)
    }

    inner class VideoHolder(inflatedView: View, audioVideoFacade: AudioVideoFacade) :
        RecyclerView.ViewHolder(inflatedView) {

        private var view: View = inflatedView
        private var audioVideo = audioVideoFacade
        fun bindVideoTile(videoCollectionTile: VideoAudioCollectionTile) {

            //set z-orderOnTop as true, as we need to show other attendee tiles on top of local-video renderer surface view,
            //else we will face glass-mirroring issue
            view.attendeeVideoSurface.setZOrderOnTop(true)

            //show attendee name of other attendees if not blank
            if (!videoCollectionTile.attendeeName.isNullOrBlank()) {
                view.tvAttendeeName.visibility = View.VISIBLE
                view.tvAttendeeName.text = videoCollectionTile.attendeeName
            } else
                view.tvAttendeeName.visibility = View.GONE

            //to show blur view if video tile is removed or not rendered, or not added
            when {
                !videoCollectionTile.isTileAdded -> {
                    view.tvPlaceholderAttendee.text = context.getString(R.string.tv_connecting)
                    view.attendeeVideoSurface.visibility = View.INVISIBLE
                    view.ivBlurViewAttendee.visibility = View.VISIBLE
                }
                videoCollectionTile.isTileRemoved -> {
                    view.attendeeVideoSurface.visibility = View.INVISIBLE
                    view.ivBlurViewAttendee.visibility = View.VISIBLE
                    view.tvPlaceholderAttendee.text =
                        context?.getString(R.string.err_video_disabled)
                }
                videoCollectionTile.isTilePaused -> {
                    view.attendeeVideoSurface.visibility = View.INVISIBLE
                    view.ivBlurViewAttendee.visibility = View.VISIBLE
                    view.tvPlaceholderAttendee.text =
                        context?.getString(R.string.err_video_paused_poor_network)
                }
                else -> {
                    view.ivBlurViewAttendee.visibility = View.VISIBLE
                    view.tvPlaceholderAttendee.text =
                        context?.getString(R.string.err_video_paused_poor_network)
                    if (videoCollectionTile.videoTileState != null)
                        audioVideo.bindVideoView(
                            view.attendeeVideoSurface,
                            videoCollectionTile.videoTileState!!.tileId
                        )
                    view.attendeeVideoSurface.visibility = View.VISIBLE
                }
            }

            //show signal strength indicators
            when (videoCollectionTile.signalStrength) {
                SignalStrength.High -> {
                    view.ivSignalStrength.setImageResource(R.drawable.ic_signal_strength_high)
                }
                SignalStrength.Low -> {
                    view.ivSignalStrength.setImageResource(R.drawable.ic_signal_strength_med)
                }
                SignalStrength.None -> {
                    view.ivSignalStrength.setImageResource(R.drawable.ic_signal_strength_low)
                }
            }

            //if too weak signal than show disabled mike icons, else show active indicators as per volume level
            if (videoCollectionTile.signalStrength == SignalStrength.None || videoCollectionTile.signalStrength == SignalStrength.Low) {

                val drawable = if (videoCollectionTile.volumeLevel == VolumeLevel.Muted) {
                    R.drawable.ic_microphone_poor_connectivity_dissabled
                } else {
                    R.drawable.ic_microphone_poor_connectivity
                }
                view.btnMuteAttendee.setImageResource(drawable)
            } else {
                when (videoCollectionTile.volumeLevel) {
                    VolumeLevel.Muted -> {
                        view.btnMuteAttendee.setImageResource(R.drawable.ic_microphone_disabled)
                    }
                    VolumeLevel.NotSpeaking -> {
                        view.btnMuteAttendee.setImageResource(R.drawable.ic_microphone_enabled)
                    }
                    VolumeLevel.Low -> {
                        view.btnMuteAttendee.setImageResource(R.drawable.ic_microphone_audio_1)
                    }
                    VolumeLevel.Medium -> {
                        view.btnMuteAttendee.setImageResource(R.drawable.ic_microphone_audio_2)
                    }
                    VolumeLevel.High -> {
                        view.btnMuteAttendee.setImageResource(R.drawable.ic_microphone_audio_3)
                    }
                }
            }

            //to show mike enabled/disabled as per attendee state
            if (videoCollectionTile.isMuted)
                view.btnMuteAttendee.setImageResource(R.drawable.ic_microphone_disabled)
        }
    }
}

