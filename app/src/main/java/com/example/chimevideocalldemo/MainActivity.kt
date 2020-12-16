package com.example.chimevideocalldemo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.chimevideocalldemo.utils.AppConstant
import com.example.chimevideocalldemo.utils.AppUtil
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

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    //AWS- region of meeting
    private val MEETING_REGION = "us-east-1"

    //AWS- unique id of meeting, can consider as room
    private var meetingID: String? = null
    private var nameSelf: String? = null

    private val Permission_Result_Code = 222

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnJoin.setOnClickListener {
            validateData()
        }
    }

    /**
     * validating meeting id and attendee name for empty or null
     * */
    private fun validateData() {
        //replacing space by + symbol, if any
        meetingID = etMeetingId?.text.toString().trim().replace("\\s+".toRegex(), "+")
        nameSelf = etName?.text.toString().trim().replace("\\s+".toRegex(), "+")

        when {
            meetingID.isNullOrBlank() -> {
                AppUtil.showToast(this, getString(R.string.err_meeting_id_invalid))
            }
            nameSelf.isNullOrBlank() -> {
                AppUtil.showToast(this, getString(R.string.err_attendee_name_invalid))
            }
            else -> {
                requestCameraPermission()
            }
        }
    }

    /**
     * request basic permissions before starting meeting
     * */
    private fun requestCameraPermission() {
        Dexter.withActivity(this).withPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                if (report.areAllPermissionsGranted()) {

                    //authenticate meeting url and begin meeting object creation steps
                    authenticate(getString(R.string.test_url), meetingID, nameSelf)
                } else {
                    showSetting()
                }
            }

            override fun onPermissionRationaleShouldBeShown(
                permissions: List<PermissionRequest>,
                token: PermissionToken
            ) {
                token.continuePermissionRequest()
            }
        }).withErrorListener { showSetting() }.onSameThread().check()
    }

    /**
     * validate meeting url, if valid
     *
     * @param attendeeName - name of attendee, entered by user
     * @param meetingId - meeting id, entered by user
     * @param meetingUrl - url of meeting fetched from serverless demo application or server
     * */
    private fun authenticate(
        meetingUrl: String,
        meetingId: String?,
        attendeeName: String?
    ) =
        uiScope.launch {

            if (!meetingUrl.startsWith("http")) {
                AppUtil.showToast(
                    applicationContext,
                    getString(R.string.user_notification_meeting_url_error)
                )
            } else {
                /*meetingResponseJson: response returned from api call, meetingResponseJson will contain
                 *all the information needed for a application to join the meeting.
                 * */
                val meetingResponseJson: String? = joinMeeting(meetingUrl, meetingId, attendeeName)

                if (meetingResponseJson == null) {
                    AppUtil.showToast(
                        applicationContext,
                        getString(R.string.user_notification_meeting_start_error)
                    )
                } else {
                    val intent = Intent(applicationContext, MeetingActivity::class.java)
                    intent.putExtra(AppConstant.BK.MEETING_RESPONSE_KEY, meetingResponseJson)
                    intent.putExtra(AppConstant.BK.MEETING_ID_KEY, meetingId)
                    intent.putExtra(AppConstant.BK.NAME_KEY, attendeeName)
                    startActivity(intent)
                }
            }
        }

    /**
     * method: to make api-call to fetch meetingResponseJson-object from serverless application
     */
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
                    "${url}join?title=${AppUtil.encodeURLParam(meetingId)}&name=${AppUtil.encodeURLParam(
                        attendeeName
                    )}&region=${AppUtil.encodeURLParam(MEETING_REGION)}"
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

                    if (responseCode == 201) {
                        response.toString()
                    } else {
                        null
                    }
                }
            } catch (exception: Exception) {
                null
            }
        }
    }

    /**
     * method: to open setting screen to grant permissions manually
     */
    private fun showSetting() {
        AppUtil.showAlert(this, getString(R.string.permission_req), IDialogCallback { isTrue ->
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
        startActivityForResult(intent, Permission_Result_Code)
    }

    /**
     * authenticate meeting url, if permissions granted from settings page
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Permission_Result_Code) {

            //authenticate meeting url and begin meeting object creation steps
            authenticate(getString(R.string.test_url), meetingID, nameSelf)
        }
    }
}