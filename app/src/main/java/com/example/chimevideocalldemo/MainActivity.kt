package com.example.chimevideocalldemo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.example.chimevideocalldemo.utils.AppConstants
import com.example.chimevideocalldemo.utils.AppUtils
import com.example.chimevideocalldemo.utils.IDialogCallback
import com.example.chimevideocalldemo.videoCall.MeetingActivity
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var meetingID: String? = null
    private var attendeeNameSelf: String? = null

    private val logger = ConsoleLogger(LogLevel.INFO)
    private val TAG: String = "MainActivity"
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val MEETING_REGION = "us-east-1"
    private val Request_Permission_Result_Code = 222
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initListener()
    }

    private fun initListener() {

        btnJoin.setOnClickListener {
            validateData()
        }
    }

    /**
     * validate if meeting id and attendee name for empty or null
     * */
    private fun validateData() {
        //replacing space by + symbol, if contains any
        meetingID = etMeetingId?.text.toString().trim().replace("\\s+".toRegex(), "+")
        attendeeNameSelf = etName?.text.toString().trim().replace("\\s+".toRegex(), "+")

        when {
            meetingID.isNullOrBlank() -> {
                AppUtils.showToast(this, getString(R.string.err_meeting_id_invalid))
            }
            attendeeNameSelf.isNullOrBlank() -> {
                AppUtils.showToast(this, getString(R.string.err_attendee_name_invalid))
            }
            else -> {
                requestCameraPermission()
            }
        }
    }

    private fun requestCameraPermission() {
        Dexter.withActivity(this).withPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                if (report.areAllPermissionsGranted()) {

                    //validate and create meeting object
                    authenticate(getString(R.string.test_url), meetingID, attendeeNameSelf)
                } else {
                    showSetting()
                }
            }

            override fun onPermissionRationaleShouldBeShown(
                permissions: List<PermissionRequest>,
                token: PermissionToken
            ) {
                token.continuePermissionRequest();
            }
        })
            .withErrorListener { showSetting() }
            .onSameThread().check()
    }

    private fun authenticate(
        meetingUrl: String,
        meetingId: String?,
        attendeeName: String?
    ) =
        uiScope.launch {
            logger.info(
                TAG,
                "Joining meeting. meetingUrl: $meetingUrl, meetingId: $meetingId, attendeeName: $attendeeName"
            )
            if (!meetingUrl.startsWith("http")) {
                AppUtils.showToast(
                    applicationContext,
                    getString(R.string.user_notification_meeting_url_error)
                )
            } else {
                //authenticationProgressBar?.visibility = View.VISIBLE
                val meetingResponseJson: String? = joinMeeting(meetingUrl, meetingId, attendeeName)

                //authenticationProgressBar?.visibility = View.INVISIBLE
                if (meetingResponseJson == null) {
                    AppUtils.showToast(
                        applicationContext,
                        getString(R.string.user_notification_meeting_start_error)
                    )
                } else {
                    val intent = Intent(applicationContext, MeetingActivity::class.java)
                    intent.putExtra(AppConstants.BK.MEETING_RESPONSE_KEY, meetingResponseJson)
                    intent.putExtra(AppConstants.BK.MEETING_ID_KEY, meetingId)
                    intent.putExtra(AppConstants.BK.NAME_KEY, attendeeName)
                    startActivity(intent)
                }
            }
        }

    private suspend fun joinMeeting(
        meetingUrl: String,
        meetingId: String?,
        attendeeName: String?
    ): String? {
        return withContext(ioDispatcher) {
            val url = if (meetingUrl.endsWith("/")) meetingUrl else "$meetingUrl/"

            //Don't forget to escape the inputs appropriately
            val serverUrl =
                URL(
                    "${url}join?title=${AppUtils.encodeURLParam(meetingId)}&name=${AppUtils.encodeURLParam(
                        attendeeName
                    )}&region=${AppUtils.encodeURLParam(MEETING_REGION)}"
                )

            try {
                val response = StringBuffer()
                with(serverUrl.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true

                    BufferedReader(InputStreamReader(inputStream)).use {
                        var inputLine = it.readLine()
                        while (inputLine != null) {
                            response.append(inputLine)
                            inputLine = it.readLine()
                        }
                        it.close()
                    }

                    if (responseCode == 200) {
                        response.toString()
                    } else {
                        logger.error(TAG, "Unable to join meeting. Response code: $responseCode")
                        null
                    }
                }
            } catch (exception: Exception) {
                logger.error(TAG, "There was an exception while joining the meeting: $exception")
                null
            }
        }
    }

    /**
     * method: to open setting screen to grant permissions manually
     */
    private fun showSetting() {
        AppUtils.showAlert(this, getString(R.string.permission_req), IDialogCallback { isTrue ->
            if (isTrue) {
                launchSetting()
            } else {
                finish()
            }
        })
    }

    /**
     * method: to launch default setting screen on device
     */
    private fun launchSetting() {
        val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
        val uri =
            Uri.fromParts("package", this.packageName, null as String?)
        intent.data = uri
        startActivityForResult(intent, Request_Permission_Result_Code)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Request_Permission_Result_Code) {

            // initialize and connect to the meeting- chime
            authenticate(getString(R.string.test_url), meetingID, attendeeNameSelf)
        }
    }
}