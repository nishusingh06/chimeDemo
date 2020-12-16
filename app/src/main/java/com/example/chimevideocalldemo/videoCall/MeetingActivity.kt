package com.example.chimevideocalldemo.videoCall

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.services.chime.sdk.meetings.audiovideo.*
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerdetector.ActiveSpeakerObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerpolicy.DefaultActiveSpeakerPolicy
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoPauseState
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoRenderView
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState
import com.amazonaws.services.chime.sdk.meetings.device.DeviceChangeObserver
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.amazonaws.services.chime.sdk.meetings.device.MediaDeviceType
import com.amazonaws.services.chime.sdk.meetings.realtime.RealtimeObserver
import com.amazonaws.services.chime.sdk.meetings.session.*
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.app.fitserv.ui.videocall.adapter.VideoAdapter
import com.app.fitserv.ui.videocall.data.JoinMeetingResponse
import com.app.fitserv.ui.videocall.data.VideoAudioCollectionTile
import com.app.fitserv.ui.videocall.data.VideoCollectionTile
import com.app.fitserv.ui.videocall.model.MeetingModel
import com.app.fitserv.ui.videocall.model.MeetingSessionModel
import com.example.chimevideocalldemo.R
import com.example.chimevideocalldemo.utils.AppConstant
import com.example.chimevideocalldemo.utils.AppUtil
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_meeting.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.ArrayList

class MeetingActivity : AppCompatActivity(), RealtimeObserver, DeviceChangeObserver,
    AudioVideoObserver, VideoTileObserver, ActiveSpeakerObserver {

    private val meetingSessionModel: MeetingSessionModel by lazy { ViewModelProvider(this)[MeetingSessionModel::class.java] }
    private val mutex = Mutex()
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val meetingModel: MeetingModel by lazy { ViewModelProvider(this)[MeetingModel::class.java] }

    //while meetingSession initialization, if exception arises then no need to clear resources in onDestroy fun
    private var isMeetingSessionNull: Boolean = false
    private var isExpertMutedOnCallInterruption: Boolean = false

    private var meetingResponseJson: String? = null
    private var attendeeNameSelf: String? = null
    private var attendeeIdLocalUser: String? = null
    private var meetingId: String? = null
    private val logger = ConsoleLogger(LogLevel.INFO)
    private val tag: String = "MeetingActivity"

    private lateinit var credentials: MeetingSessionCredentials
    private lateinit var audioVideo: AudioVideoFacade
    private lateinit var videoTileAdapter: VideoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting)

        getIntentData()
        initUi()
    }

    /**
     * method: to get data from previous activity i.e., meetingId, meetingResponseJson and attendeeName
     */
    private fun getIntentData() {
        val bundle: Bundle? = intent.extras
        if (bundle != null) {

            meetingId = bundle.getString(AppConstant.BK.MEETING_ID_KEY)
            attendeeNameSelf = bundle.getString(AppConstant.BK.NAME_KEY)
            meetingResponseJson = bundle.getString(AppConstant.BK.MEETING_RESPONSE_KEY)
        }
    }

    private fun initUi() {
        if (!meetingResponseJson.isNullOrBlank()) {

            //show beat animation
            startBeatAnimation()
            doSessionConfig(meetingResponseJson)
        } else {
            AppUtil.showToast(
                this@MeetingActivity,
                getString(R.string.user_notification_meeting_start_error)
            )
            Log.i(tag, "initUi: meetingResponseJson isNullOrBlank")
        }
    }

    /**
     * method: we will pass api-response to createSessionConfiguration fun
     * and pass its response to chime constructor to return meetingSession object
     */
    private fun doSessionConfig(meetingResponseJson: String?) {
        val sessionConfig = createSessionConfiguration(meetingResponseJson)
        val meetingSession = sessionConfig?.let {

            Log.i(tag, "Creating meeting session for meeting Id: $meetingId")
            logger.info(tag, "Creating meeting session for meeting Id: $meetingId")
            DefaultMeetingSession(
                it,
                logger,
                applicationContext
            )
        }

        if (meetingSession == null) {
            //if configuration fails, then finish activity
            isMeetingSessionNull = true

            Log.i(tag, "meetingSession == null, finish activity abruptly")
            AppUtil.showToast(this, getString(R.string.user_notification_meeting_start_error))
            finish()
        } else {
            Log.i(tag, "meetingSession object created successfully")
            meetingSessionModel.setMeetingSession(meetingSession)

            //before starting meeting -acquire audio focus
            requestAudioFocus()
        }
    }

    /**
     * method: consume meetingResponseJson object for session configuration
     */
    private fun createSessionConfiguration(response: String?): MeetingSessionConfiguration? {
        if (response.isNullOrBlank()) return null

        return try {
            val gson = Gson()
            val joinMeetingResponse = gson.fromJson(response, JoinMeetingResponse::class.java)
            attendeeIdLocalUser = joinMeetingResponse.joinInfo.attendeeResponse.attendee.AttendeeId
            Log.i(
                tag,
                "chime SessionConfiguration completed called for attendeeIdSelf: $attendeeIdLocalUser"
            )

            MeetingSessionConfiguration(
                CreateMeetingResponse(joinMeetingResponse.joinInfo.meetingResponse.meeting),
                CreateAttendeeResponse(joinMeetingResponse.joinInfo.attendeeResponse.attendee),
                ::urlRewriter
            )
        } catch (exception: Exception) {
            logger.error(
                tag,
                "Error creating session configuration: ${exception.localizedMessage}"
            )
            Log.i(tag, "chime SessionConfiguration encountered error: null returned")
            null
        }
    }

    fun getAudioVideo(): AudioVideoFacade = meetingSessionModel.audioVideo
    fun getMeetingSessionCredentials(): MeetingSessionCredentials = meetingSessionModel.credentials

    private fun urlRewriter(url: String): String {
        // You can change urls by url.replace("example.com", "my.example.com")
        return url
    }

    /**
     * method: to render UI elements, register listeners and adapter, gain audio focus and start meeting
     */
    private fun startCalling() {
        Handler(Looper.getMainLooper()).postDelayed({

            //initialize audioVideo facade
            audioVideo = getAudioVideo()
            credentials = getMeetingSessionCredentials()

            //show text placeholder as waiting
            tvPlaceholder.text = getString(R.string.tv_waiting)

            //Start audio and video clients.
            audioVideo.start()

            //start local video rendering and streaming
            audioVideo.startLocalVideo()
            SelfVideoSurface.visibility = View.VISIBLE

            //start streaming remote videos
            audioVideo.startRemoteVideo()
            meetingModel.isCameraOn = true

            //need to handle real-time scenario like - receiving phone call in middle of meeting
            val telephonyManager: TelephonyManager =
                getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)

            //set preferred media type as speaker, by default it's build-in-speaker
            val list: List<MediaDevice> =
                audioVideo.listAudioDevices()
                    .filter { device -> device.type != MediaDeviceType.OTHER }

            meetingModel.currentMediaDevices = list.toMutableList();
            setPreferredMediaDeviceType(meetingModel.currentMediaDevices)

            //initialize adapter and chime real-time observers
            initActionButtonsListener()
            initAdapter()
            subscribeToAttendeeChangeHandlers()

            Log.i(tag, "startVideoCalling after beat animation, sticky service & timer started")
            clearBeatAnimation()
        }, 2000)
    }

    private lateinit var mAudioManager: AudioManager
    private lateinit var playbackAttributes: AudioAttributes
    private lateinit var mAudioFocusRequest: AudioFocusRequest
    var audioFocusRequestDelayed = false
    var audioFocusAuthorized = false

    /**
     * method: to request audio focus for our application, if any other app is using at the same time than
     * we will use AudioManager to gain audio-focus and pause other application
     * */
    private fun requestAudioFocus() {

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        mAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(afChangeListener, handler)
            .build()

        val focusRequest: Int = mAudioManager.requestAudioFocus(mAudioFocusRequest)
        val focusLock = Any()

        synchronized(focusLock) {
            if (focusRequest == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                //if failed to gain audio-focus, keep trace and try again
                //after some time while starting audio and video stream
                audioFocusAuthorized = false
                startCalling()
            } else if (focusRequest == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocusAuthorized = true
                startCalling()
            } else if (focusRequest == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                audioFocusRequestDelayed = true
                audioFocusAuthorized = false
            }
        }
    }

    /**
     * afChangeListener listener: it will observe and notify if audio-focus requested by other application
     * while we are in foreground
     * */
    private var handler = Handler(Looper.getMainLooper())
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss of audio focus
                // we will Pause our audio immediately
                if (!meetingModel.isMuted) {
                    audioVideo.realtimeLocalMute()
                    meetingModel.isMuted = true
                    isExpertMutedOnCallInterruption = true
                    ivMuteIcon.setImageResource(R.drawable.ic_mike_off)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback
                if (!meetingModel.isMuted) {
                    audioVideo.realtimeLocalMute()
                    meetingModel.isMuted = true
                    isExpertMutedOnCallInterruption = true
                    ivMuteIcon.setImageResource(R.drawable.ic_mike_off)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower the volume, keep playing
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // resume audio playback
                if (isExpertMutedOnCallInterruption) {
                    audioVideo.realtimeLocalUnmute()
                    meetingModel.isMuted = false
                    isExpertMutedOnCallInterruption = false
                    ivMuteIcon.setImageResource(R.drawable.ic_mike_on)
                }
            }
        }
    }

    /**
     * method: to release audio focus, when meeting ends
     * */
    private fun abandonAudioFocus() {
        mAudioManager.abandonAudioFocus(afChangeListener)
    }

    /**
     * listener class for monitoring changes in specific telephony states on the device.
     * */
    private var phoneStateListener: PhoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    //INCOMING call
                    // Pause our audio streaming, if user is not muted
                    if (!meetingModel.isMuted) {
                        audioVideo.realtimeLocalMute()
                        meetingModel.isMuted = true
                        isExpertMutedOnCallInterruption = true
                        ivMuteIcon.setImageResource(R.drawable.ic_mike_off)
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    //Not IN CALL
                    // resume audio streaming
                    if (isExpertMutedOnCallInterruption) {
                        audioVideo.realtimeLocalUnmute()
                        meetingModel.isMuted = false
                        isExpertMutedOnCallInterruption = false
                        ivMuteIcon.setImageResource(R.drawable.ic_mike_on)
                    }
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    //CALL IS ATTENDED
                    // Pause our audio streaming, if user is not muted
                    if (!meetingModel.isMuted) {
                        audioVideo.realtimeLocalMute()
                        meetingModel.isMuted = true
                        isExpertMutedOnCallInterruption = true
                        ivMuteIcon.setImageResource(R.drawable.ic_mike_off)
                    }
                }
            }

            super.onCallStateChanged(state, incomingNumber)
        }
    } //end PhoneStateListener

    /**
     *method: to mute and umute self
     * */
    private fun toggleMute() {
        if (meetingModel.isMuted) {
            //unmute local attendee
            audioVideo.realtimeLocalUnmute()
            ivMuteIcon.setImageResource(R.drawable.ic_mike_on)
        } else {
            //mute local attendee
            audioVideo.realtimeLocalMute()
            ivMuteIcon.setImageResource(R.drawable.ic_mike_off)
        }
        meetingModel.isMuted = !meetingModel.isMuted
    }

    /**
     *method: to enable and disable self video
     * */
    private fun toggleVideo() {
        if (meetingModel.isCameraOn) {
            //stops local video streaming
            audioVideo.stopLocalVideo()
            tvPlaceholder2.text = "Video sharing is disabled!"
            tvPlaceholder2.visibility = View.VISIBLE
            ltRoot.background =
                ContextCompat.getDrawable(this@MeetingActivity, R.drawable.ic_blur_view_call)
            ivVideoIcon.setImageResource(R.drawable.ic_video_off)
        } else {
            startLocalVideo()
        }
        meetingModel.isCameraOn = !meetingModel.isCameraOn
    }

    /**
     *method: to enable local video streaming,
     * */
    private fun startLocalVideo() {
        //starts local video streaming
        audioVideo.startLocalVideo()
        tvPlaceholder2.visibility = View.GONE
        ltRoot.background = null
        ltRoot.setBackgroundColor(ContextCompat.getColor(this@MeetingActivity, R.color.color_white))
        ivVideoIcon.setImageResource(R.drawable.ic_video_on)
        SelfVideoSurface.visibility = View.VISIBLE
    }

    private fun initActionButtonsListener() {

        ivHangUp.setOnClickListener {
            clearResources()
        }

        ivMuteIcon.setImageResource(if (meetingModel.isMuted) R.drawable.ic_mike_off else R.drawable.ic_mike_on)
        ivMuteIcon.setOnClickListener { toggleMute() }

        ivVideoIcon.setImageResource(if (meetingModel.isCameraOn) R.drawable.ic_video_off else R.drawable.ic_video_on)
        ivVideoIcon.setOnClickListener { toggleVideo() }

        //method to switch back and front camera
        ivCameraFlip.setOnClickListener {

            uiScope.launch {
                mutex.withLock {
                    //many video conferencing sdk's follow mirror video rule
                    val cameraType = audioVideo.getActiveCamera()
                    audioVideo.switchCamera()
                    if (cameraType == MediaDevice("1", MediaDeviceType.VIDEO_FRONT_CAMERA)) {
                        //if previous state was front camera, than we will not mirror Surface-view
                        SelfVideoSurface.setMirror(false)
                    } else {
                        //if previous state was back camera, than we will mirror Surface-view
                        SelfVideoSurface.setMirror(true)
                    }
                }
            }
        }
    }

    private fun initAdapter() {

        // Video (camera & content)
        rvVideoCollection.layoutManager =
            LinearLayoutManager(this@MeetingActivity, RecyclerView.HORIZONTAL, true)
        videoTileAdapter = VideoAdapter(
            meetingModel.currentVideoAudioTiles.values,
            audioVideo,
            this@MeetingActivity
        )
        rvVideoCollection.adapter = videoTileAdapter
        rvVideoCollection.visibility = View.VISIBLE
    }

    override fun onAttendeesDropped(attendeeInfo: Array<AttendeeInfo>) {
        uiScope.launch {
            mutex.withLock {
                attendeeInfo.forEach { (attendeeId, _) ->
                    if (meetingModel.currentVideoAudioTiles[attendeeId] != null && !meetingModel.currentVideoAudioTiles[attendeeId]?.attendeeName.isNullOrBlank())
                        AppUtil.showToast(
                            this@MeetingActivity,
                            "${meetingModel.currentVideoAudioTiles[attendeeId]?.attendeeName} has dropped from the session."
                        )

                    meetingModel.currentVideoAudioTiles.remove(
                        attendeeId
                    )
                    Log.i(
                        tag,
                        "onAttendeesDropped fun called- for attendeeId: $attendeeId and attendee successfully removed from list"
                    )
                    videoTileAdapter.notifyDataSetChanged()

                    if (meetingModel.currentVideoAudioTiles.isEmpty()) {
                        tvPlaceholder.visibility = View.VISIBLE
                        Log.i(tag, "tvPlaceholder visible as list size was 0")
                    }
                }
            }
        }
    }

    override fun onAttendeesJoined(attendeeInfo: Array<AttendeeInfo>) {
        uiScope.launch {
            mutex.withLock {
                attendeeInfo.forEach { (attendeeId, externalUserId) ->
                    if (!attendeeIdLocalUser.isNullOrBlank() && !attendeeIdLocalUser.equals(
                            attendeeId,
                            true
                        )
                    ) {

                        meetingModel.currentVideoAudioTiles.getOrPut(
                            attendeeId,
                            {
                                VideoAudioCollectionTile(
                                    attendeeId,
                                    getAttendeeName(attendeeId, externalUserId),
                                    VolumeLevel.NotSpeaking,
                                    SignalStrength.High,
                                    false,
                                    null,
                                    isMuted = false,
                                    isTileRemoved = false,
                                    isTilePaused = false,
                                    isTileAdded = false
                                )
                            })

                        AppUtil.showToast(
                            this@MeetingActivity, "${getAttendeeName(
                                attendeeId,
                                externalUserId
                            )} has joined the session."
                        )

                        tvPlaceholder.visibility = View.GONE
                        Log.i(
                            tag,
                            "onAttendeesJoined fun- placeholder removed as- attendeeIdSelf: $attendeeIdLocalUser + attendeeId $attendeeId not matched"
                        )
                        Log.i(
                            tag,
                            "videoAudioTilesList size: ${meetingModel.currentVideoAudioTiles.size}"
                        )
                    } else {
                        Log.i(
                            tag,
                            "onAttendeesJoined fun placeholder not removed as- attendeeIdSelf: $attendeeIdLocalUser + and  attendeeId $attendeeId matched"
                        )
                    }
                    videoTileAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    // Check if attendee Id contains this at the end to identify content share
    private val contentDelimiter = "#content"

    // Append to attendee name if it's for content share
    private val contentNameSuffix = "<<Content>>"
    private fun getAttendeeName(attendeeId: String, externalUserId: String): String {
        val attendeeName = externalUserId.split('#')[1]

        Log.i(tag, "getAttendeeName fun: attendeeName: $attendeeName for attendeeId: $attendeeId")
        return if (attendeeId.endsWith(contentDelimiter)) {
            "$attendeeName $contentNameSuffix"
        } else {
            attendeeName
        }
    }

    override fun onAttendeesLeft(attendeeInfo: Array<AttendeeInfo>) {
        uiScope.launch {
            mutex.withLock {
                attendeeInfo.forEach { (attendeeId, _) ->
                    if (meetingModel.currentVideoAudioTiles[attendeeId] != null && !meetingModel.currentVideoAudioTiles[attendeeId]?.attendeeName.isNullOrBlank())
                        AppUtil.showToast(
                            this@MeetingActivity,
                            "${meetingModel.currentVideoAudioTiles[attendeeId]?.attendeeName} has left the session."
                        )

                    meetingModel.currentVideoAudioTiles.remove(
                        attendeeId
                    )

                    Log.i(
                        tag,
                        "onAttendeesLeft fun called- for attendeeId: $attendeeId and attendee successfully removed from list"
                    )
                    videoTileAdapter.notifyDataSetChanged()

                    if (meetingModel.currentVideoAudioTiles.isEmpty()) {
                        tvPlaceholder.visibility = View.VISIBLE
                        Log.i(tag, "tvPlaceholder visible as list size was 0")
                    }
                }
            }
        }
    }

    private var videoCollectionTiles: Collection<VideoAudioCollectionTile>? = null
    private var position: Int = 0
    override fun onAttendeesMuted(attendeeInfo: Array<AttendeeInfo>) {
        val size = attendeeInfo.size
        for (i in 0 until size) {

            val attendee: AttendeeInfo = attendeeInfo[i]
            if (!attendeeIdLocalUser.equals(attendee.attendeeId, true)) {

                //to get the index of attendee tile
                videoCollectionTiles = meetingModel.currentVideoAudioTiles.values
                position =
                    videoCollectionTiles!!.indexOf(meetingModel.currentVideoAudioTiles[attendee.attendeeId])

                meetingModel.currentVideoAudioTiles[attendee.attendeeId]?.let {
                    meetingModel.currentVideoAudioTiles[attendee.attendeeId] =
                        VideoAudioCollectionTile(
                            it.attendeeId,
                            it.attendeeName,
                            VolumeLevel.Muted,
                            it.signalStrength,
                            it.isActiveSpeaker,
                            it.videoTileState,
                            true,
                            it.isTileRemoved,
                            it.isTilePaused,
                            it.isTileAdded
                        )
                    videoTileAdapter.notifyItemChanged(position)

                    Log.i(
                        tag,
                        "onAttendeesMuted fun called- for attendeeId: ${attendee.attendeeId} and attendee successfully muted notified adapter"
                    )
                    return
                }
            } else {
                Log.i(
                    tag,
                    "onAttendeesMuted fun called- muted to self- attendeeIdSelf: $attendeeIdLocalUser"
                )
            }
        }
    }

    override fun onAttendeesUnmuted(attendeeInfo: Array<AttendeeInfo>) {
        val size = attendeeInfo.size
        for (i in 0 until size) {

            val attendee: AttendeeInfo = attendeeInfo[i]
            if (!attendeeIdLocalUser.equals(attendee.attendeeId, true)) {

                videoCollectionTiles = meetingModel.currentVideoAudioTiles.values
                position =
                    videoCollectionTiles!!.indexOf(meetingModel.currentVideoAudioTiles[attendee.attendeeId])

                meetingModel.currentVideoAudioTiles[attendee.attendeeId]?.let {
                    meetingModel.currentVideoAudioTiles[attendee.attendeeId] =
                        VideoAudioCollectionTile(
                            it.attendeeId,
                            it.attendeeName,
                            VolumeLevel.NotSpeaking,
                            it.signalStrength,
                            it.isActiveSpeaker,
                            it.videoTileState,
                            false,
                            it.isTileRemoved,
                            it.isTilePaused,
                            it.isTileAdded
                        )
                    videoTileAdapter.notifyItemChanged(position)

                    Log.i(
                        tag,
                        "onAttendeesUnmuted fun called- for attendeeId: ${attendee.attendeeId} and attendee successfully Unmuted notified adapter"
                    )
                    return
                }
            } else {
                Log.i(
                    tag,
                    "onAttendeesMuted fun called- muted to self- attendeeIdSelf: $attendeeIdLocalUser"
                )
            }
        }
    }

    override fun onSignalStrengthChanged(signalUpdates: Array<SignalUpdate>) {
        uiScope.launch {
            mutex.withLock {

                val size = signalUpdates.size
                for (i in 0 until size) {

                    val signalUpdate: SignalUpdate = signalUpdates[i]
                    //to get the index of addentee tile
                    videoCollectionTiles = meetingModel.currentVideoAudioTiles.values
                    position =
                        videoCollectionTiles!!.indexOf(meetingModel.currentVideoAudioTiles[signalUpdate.attendeeInfo.attendeeId])

                    meetingModel.currentVideoAudioTiles[signalUpdate.attendeeInfo.attendeeId]?.let {

                        Log.i(
                            tag,
                            "onSignalStrengthChanged fun: for attendeeId ${signalUpdate.attendeeInfo.attendeeId} signal strength is: ${signalUpdate.signalStrength}"
                        )
                        meetingModel.currentVideoAudioTiles[signalUpdate.attendeeInfo.attendeeId] =
                            VideoAudioCollectionTile(
                                it.attendeeId,
                                it.attendeeName,
                                it.volumeLevel,
                                signalUpdate.signalStrength,
                                it.isActiveSpeaker,
                                it.videoTileState,
                                it.isMuted,
                                it.isTileRemoved,
                                it.isTilePaused,
                                it.isTileAdded
                            )
                        videoTileAdapter.notifyItemChanged(position)
                    }
                    break
                }
            }
        }
    }

    override fun onVolumeChanged(volumeUpdates: Array<VolumeUpdate>) {
        uiScope.launch {
            mutex.withLock {

                val size = volumeUpdates.size
                if (meetingModel.currentVideoAudioTiles.isNotEmpty() && !attendeeIdLocalUser.isNullOrBlank()) {
                    for (i in 0 until size) {

                        val volumeUpdate = volumeUpdates[i]
                        if (!attendeeIdLocalUser.equals(
                                volumeUpdate.attendeeInfo.attendeeId,
                                true
                            )
                        ) {

                            //to get the index of addentee tile
                            videoCollectionTiles = meetingModel.currentVideoAudioTiles.values
                            position =
                                videoCollectionTiles!!.indexOf(meetingModel.currentVideoAudioTiles[volumeUpdate.attendeeInfo.attendeeId])

                            meetingModel.currentVideoAudioTiles[volumeUpdate.attendeeInfo.attendeeId]?.let {

                                Log.i(
                                    tag,
                                    "onVolumeChanged fun: for attendeeId ${volumeUpdate.attendeeInfo.attendeeId} volumeLevel is: ${volumeUpdate.volumeLevel}"
                                )
                                meetingModel.currentVideoAudioTiles[volumeUpdate.attendeeInfo.attendeeId] =
                                    VideoAudioCollectionTile(
                                        it.attendeeId,
                                        it.attendeeName,
                                        volumeUpdate.volumeLevel,
                                        it.signalStrength,
                                        it.isActiveSpeaker,
                                        it.videoTileState,
                                        it.isMuted,
                                        it.isTileRemoved,
                                        it.isTilePaused,
                                        it.isTileAdded
                                    )
                                videoTileAdapter.notifyItemChanged(position)
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    override fun onAudioDeviceChanged(freshAudioDeviceList: List<MediaDevice>) {
        meetingModel.currentMediaDevices = freshAudioDeviceList as MutableList<MediaDevice>
        setPreferredMediaDeviceType(freshAudioDeviceList)
        Log.i(tag, "onAudioDeviceChanged fun called")
    }

    private fun setPreferredMediaDeviceType(audioDeviceList: MutableList<MediaDevice>?) {
        if (audioDeviceList!!.isNotEmpty()) {

            var audioDevices: MutableList<MediaDevice>? = null

            audioDevices?.addAll(audioDeviceList)
            audioDevices?.sortBy { it.order }

            audioDevices?.remove(MediaDeviceType.AUDIO_HANDSET)
            if (audioDevices?.isNotEmpty() == true) {
                audioVideo.chooseAudioDevice(audioDevices[0])
                Log.i(tag, "onAudioDeviceChanged fun: MediaDeviceType: ${audioDevices[0].type}")
            }
        }
    }

    override fun onAudioSessionCancelledReconnect() {
        tvPlaceholderConnecting.text = "Unable to connect. Please disconnect and rejoin the session"
        tvPlaceholderConnecting.visibility = View.VISIBLE
        Log.i(tag, "onAudioSessionCancelledReconnect fun called- Audio cancelled reconnecting")
    }

    override fun onAudioSessionDropped() {
        Log.i(tag, "onAudioSessionDropped fun called- Audio session dropped")
        tvPlaceholderConnecting.text = "Reconnecting to the session"
        tvPlaceholderConnecting.visibility = View.VISIBLE
    }

    override fun onAudioSessionStarted(reconnecting: Boolean) {
        if (reconnecting) {
            Log.i(tag, "onAudioSessionStarted fun called- Audio reconnected successfully.")
            tvPlaceholderConnecting.visibility = View.GONE
        } else {
            Log.i(tag, "onAudioSessionStarted fun called- Audio successfully started.")
        }
    }

    override fun onAudioSessionStartedConnecting(reconnecting: Boolean) {
        if (reconnecting) {
            Log.i(tag, "onAudioSessionStartedConnecting fun called- Audio started reconnecting.")
            tvPlaceholderConnecting.text = "Reconnecting to the session"
        }
    }

    override fun onAudioSessionStopped(sessionStatus: MeetingSessionStatus) {
        Log.i(tag, "onAudioSessionStopped fun called- Audio stopped due to poor network.")

        if (sessionStatus.statusCode != MeetingSessionStatusCode.OK) {
            AppUtil.showToast(
                this@MeetingActivity,
                "Session has been Dropped due to poor network connection. Please try to reconnect again."
            )
            clearResources()
            Log.i(
                tag,
                "onAudioSessionStopped fun called- closeActivity called ${MeetingSessionStatusCode.OK}"
            )
            finish()
        }
    }

    override fun onConnectionBecamePoor() {
        Log.i(
            tag,
            "onConnectionBecamePoor fun called- Connection quality has become poor connection."
        )
        tvPlaceholderConnecting.text = "Poor connection"
        tvPlaceholderConnecting.visibility = View.VISIBLE
    }

    override fun onConnectionRecovered() {
        Log.i(tag, "onConnectionRecovered fun called- Connection quality has recovered")
        tvPlaceholderConnecting.visibility = View.GONE
    }

    override fun onVideoSessionStarted(sessionStatus: MeetingSessionStatus) {
        val message =
            if (sessionStatus.statusCode == MeetingSessionStatusCode.VideoAtCapacityViewOnly) "Video stream encountered an error: ${sessionStatus.statusCode}" else "Video successfully started."

        Log.i(tag, message)
        tvPlaceholderConnecting.visibility = View.GONE
    }

    override fun onVideoSessionStartedConnecting() {
        Log.i(tag, "onVideoSessionStartedConnecting fun called- Video started connecting.")

        tvPlaceholderConnecting.text = "Connecting..."
        tvPlaceholderConnecting.visibility = View.VISIBLE
    }

    override fun onVideoSessionStopped(sessionStatus: MeetingSessionStatus) {
        Log.i(tag, "Video stopped due to poor network connection.")

        tvPlaceholderConnecting.text = "Poor network quality. Please check your network connection."
        tvPlaceholderConnecting.visibility = View.VISIBLE
    }

    override fun onVideoTileAdded(tileState: VideoTileState) {
        uiScope.launch {

            if (tileState.isLocalTile) {

                //save tileState to keep track of local user video stream
                saveLocalVideoTile(tileState)
                val attendeeName =
                    meetingModel.currentVideoAudioTiles[tileState.attendeeId]?.attendeeName
                        ?: ""
                val attendeeCollection =
                    VideoCollectionTile(attendeeName, tileState, false, false)

                SelfVideoSurface.setZOrderOnTop(false)
                SelfVideoSurface.setMirror(true)
                SelfVideoSurface.visibility = View.VISIBLE
                audioVideo.bindVideoView(
                    SelfVideoSurface as VideoRenderView,
                    attendeeCollection.videoTileState.tileId
                )
            } else if (!meetingModel.currentVideoAudioTiles.containsKey(tileState.tileId)) {
                showRemoteVideoTile(tileState)
            } else {
                Log.i(tag, "meetingModel.currentVideoAudioTiles does not contain tileID")
            }
        }
    }

    private fun showRemoteVideoTile(tileState: VideoTileState) {
        meetingModel.currentVideoTiles[tileState.tileId] =
            createLocalVideoCollectionTile(tileState)

        val mAudioVideoList = ArrayList(meetingModel.currentVideoAudioTiles.values)
        for ((index, audioVideoTile) in mAudioVideoList.withIndex()) {
            if (audioVideoTile.attendeeId.equals(tileState.attendeeId, true)) {

                audioVideoTile.videoTileState = tileState
                audioVideoTile.isTileRemoved = false
                audioVideoTile.isTileAdded = true

                videoTileAdapter.notifyItemChanged(index)
                break
            } else {
                Log.i(tag, " attendee id does  not existing in addedAttendees list")
            }
        }
    }

    private lateinit var selfVideoTileInstance: VideoCollectionTile
    private fun saveLocalVideoTile(tileState: VideoTileState) {
        selfVideoTileInstance =
            createLocalVideoCollectionTile(tileState)
    }

    private fun createLocalVideoCollectionTile(tileState: VideoTileState): VideoCollectionTile {
        val attendeeId = tileState.attendeeId
        Log.i(tag, "createVideoCollectionTile fun called for attendeeId: $attendeeId")
        attendeeId?.let {
            Log.d(tag, attendeeId)
            val attendeeName = meetingModel.currentVideoAudioTiles[attendeeId]?.attendeeName
                ?: ""
            Log.i(
                tag,
                "createVideoCollectionTile fun: called for attendeeId: $attendeeId attendee object created with param attendeeName: $attendeeName , isMuted: true and isTileRemoved: true"
            )
            return VideoCollectionTile(
                attendeeName,
                tileState,
                isMuted = false,
                isTileRemoved = false
            )
        }

        return VideoCollectionTile("", tileState, false, false)
    }

    private fun pauseRemoteVideoTile(tileState: VideoTileState, isPaused: Boolean) {

        val mAudioVideoList = ArrayList(meetingModel.currentVideoAudioTiles.values)
        for ((index, audioVideoTile) in mAudioVideoList.withIndex()) {
            if (audioVideoTile.attendeeId.equals(tileState.attendeeId, true)) {

                audioVideoTile.videoTileState = tileState
                audioVideoTile.isTilePaused = isPaused
                Log.i(
                    tag,
                    "showRemoteVideoTile fun: attendee id existing in addedAttendees list in showRemoteVideoTile() and isTileRemoved=false"
                )
                videoTileAdapter.notifyItemChanged(index)
                break
            } else {
                Log.i(tag, " attendee id does  not existing in addedAttendees list")
            }
        }

        Log.i(
            tag,
            "after createVideoCollectionTile fun call- currentVideoAudioTiles list size: ${meetingModel.currentVideoAudioTiles.size}"
        )
    }

    override fun onVideoTilePaused(tileState: VideoTileState) {
        if (tileState.pauseState == VideoPauseState.PausedForPoorConnection) {
            val attendeeName =
                meetingModel.currentVideoAudioTiles[tileState.attendeeId]?.attendeeName
                    ?: ""

            if (!tileState.isLocalTile) {
                if (!meetingModel.currentVideoAudioTiles.containsKey(tileState.tileId)) {
                    pauseRemoteVideoTile(tileState, true)
                } else {
                    Log.i(
                        tag,
                        "meetingModel.currentVideoAudioTiles does not contain tileID"
                    )
                }
            } else {
                ltRoot.background =
                    ContextCompat.getDrawable(this@MeetingActivity, R.drawable.ic_blur_view_call)
                SelfVideoSurface.visibility = View.GONE
                tvPlaceholder2.text = "Reconnecting..."
                tvPlaceholder2.visibility = View.VISIBLE

                Log.i(tag, "onVideoTilePaused local")
            }

            Log.i(tag, "onVideoTilePaused fun attendeeName: $attendeeName")
        }
    }

    override fun onVideoTileRemoved(tileState: VideoTileState) {
        uiScope.launch {
            val tileId: Int = tileState.tileId

            if (!tileState.isLocalTile) {

                if (meetingModel.currentVideoTiles.containsKey(tileId))
                    meetingModel.currentVideoTiles.remove(tileId)

                audioVideo.unbindVideoView(tileId)
                val mAttendeeList = ArrayList(meetingModel.currentVideoAudioTiles.values)

                for (videoTile in mAttendeeList) {
                    if (videoTile.videoTileState?.attendeeId.equals(tileState.attendeeId, true)) {
                        meetingModel.currentVideoAudioTiles[tileState.attendeeId]?.isTileRemoved =
                            true
                        Log.i(tag, "for attendeeId: ${tileState.attendeeId} tile has been removed")
                        videoTileAdapter.notifyDataSetChanged()
                        break
                    }
                }
            } else {
                ltRoot.background =
                    ContextCompat.getDrawable(this@MeetingActivity, R.drawable.ic_blur_view_call)
                SelfVideoSurface.visibility = View.GONE
                if (meetingModel.isCameraOn)
                    tvPlaceholder2.text = "Reconnecting..."
                tvPlaceholder2.visibility = View.VISIBLE
                Log.i(tag, "onVideoTileRemoved local")
            }
        }
        Log.i(tag, "onVideoTileRemoved fun called")
    }

    override fun onVideoTileResumed(tileState: VideoTileState) {
        val attendeeName = meetingModel.currentVideoAudioTiles[tileState.attendeeId]?.attendeeName
            ?: ""

        //check if local or remote tile
        if (!tileState.isLocalTile) {
            if (!meetingModel.currentVideoAudioTiles.containsKey(tileState.tileId)) {
                pauseRemoteVideoTile(tileState, false)
            } else {
                Log.i(tag, "meetingModel.currentVideoTiles does not contain tileID")
            }
        } else {
            startLocalVideo()
        }
        Log.i(tag, "onVideoTileResumed fun attendeeName: $attendeeName")
    }

    override fun onVideoTileSizeChanged(tileState: VideoTileState) {
    }

    override val scoreCallbackIntervalMs: Int?
        get() = TODO("ndcdn")

    override fun onActiveSpeakerDetected(attendeeInfo: Array<AttendeeInfo>) {
        uiScope.launch {
            mutex.withLock {

                val activeSpeakers = attendeeInfo.map { it.attendeeId }.toSet()
                val audioVideoCollection = ArrayList(meetingModel.currentVideoAudioTiles.values)
                for ((index, value) in audioVideoCollection.withIndex()) {

                    if (!attendeeIdLocalUser.equals(value.attendeeId, true)) {
                        if (activeSpeakers.contains(value.attendeeId) != value.isActiveSpeaker) {
                            //to get the index of addentee tile
                            videoCollectionTiles = meetingModel.currentVideoAudioTiles.values
                            position =
                                videoCollectionTiles!!.indexOf(meetingModel.currentVideoAudioTiles[value.attendeeId])

                            meetingModel.currentVideoAudioTiles[value.attendeeId]?.let {
                                meetingModel.currentVideoAudioTiles[value.attendeeId] =
                                    VideoAudioCollectionTile(
                                        it.attendeeId,
                                        it.attendeeName,
                                        it.volumeLevel,
                                        it.signalStrength,
                                        !it.isActiveSpeaker,
                                        it.videoTileState,
                                        it.isMuted,
                                        it.isTileRemoved,
                                        it.isTilePaused,
                                        it.isTileAdded
                                    )
                                videoTileAdapter.notifyItemChanged(index)
                                Log.i(
                                    tag,
                                    "onAttendeesUnmuted fun called- for attendeeId: ${value.attendeeId} and attendee successfully Unmuted notified adapter"
                                )
                            }
                        }
                    }
                    return@withLock
                }
            }
        }
    }

    override fun onActiveSpeakerScoreChanged(scores: Map<AttendeeInfo, Double>) {
    }

    /**
     * method overridden: to unbind video views and stop audioVideo
     */
    override fun onDestroy() {

        //true when meetingSessionModel has not initialized i.e., meetingSessionModel==null skip resources cleanup
        if (!isMeetingSessionNull)
            clearResources()
        else {
            unsubscribeFromAttendeeChangeHandlers()
            meetingModel.currentVideoTiles.forEach { (tileId, tileData) ->
                audioVideo.unbindVideoView(tileId)
            }
        }
        super.onDestroy()
    }

    private fun clearResources() {
        Log.i(tag, "clearResources fun called")

        unsubscribeFromAttendeeChangeHandlers()
        meetingModel.currentVideoTiles.forEach { (tileId, tileData) ->
            audioVideo.unbindVideoView(tileId)
        }
        /*  selfVideoTileInstance.forEach { (tileId, tileData) ->
              audioVideo.unbindVideoView(tileId)
          }*/

        //unbind local video tile
        audioVideo.unbindVideoView(selfVideoTileInstance.videoTileState.tileId)

        abandonAudioFocus()
        //stop local and remote view rendering
        audioVideo.stopLocalVideo()
        audioVideo.stopRemoteVideo()
        //stop audio and video client
        meetingSessionModel.audioVideo.stop()
        finish()
    }

    /**
     * method: to start beat i.e., zoom in and out animation on userProfile pic
     */
    private fun startBeatAnimation() {
        val an = AnimationUtils.loadAnimation(this@MeetingActivity, R.anim.beat)
        an.reset()
        ltProfilePic.visibility = View.VISIBLE
        ltProfilePic.clearAnimation()
        ltProfilePic.startAnimation(an)
    }

    private fun clearBeatAnimation() {
        ltProfilePic.visibility = View.GONE
        ltProfilePic.clearAnimation()
    }

    private fun subscribeToAttendeeChangeHandlers() {
        audioVideo.addAudioVideoObserver(this)
        audioVideo.addDeviceChangeObserver(this)
        audioVideo.addRealtimeObserver(this)
        audioVideo.addVideoTileObserver(this)
        audioVideo.addActiveSpeakerObserver(DefaultActiveSpeakerPolicy(), this)
        Log.i(tag, "chime handlers added")
    }

    private fun unsubscribeFromAttendeeChangeHandlers() {
        audioVideo.removeAudioVideoObserver(this)
        audioVideo.removeDeviceChangeObserver(this)
        audioVideo.removeRealtimeObserver(this)
        audioVideo.removeVideoTileObserver(this)
        audioVideo.removeActiveSpeakerObserver(this)
        Log.i(tag, "chime handlers released")
    }

    /**
     * method overridden: to show popup dialog on back press
     */
    override fun onBackPressed() {
        Log.i(tag, "onBackPressed called")
        clearResources()
    }
}