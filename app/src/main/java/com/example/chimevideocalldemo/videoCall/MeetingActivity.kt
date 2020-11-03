package com.example.chimevideocalldemo.videoCall

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.chimevideocalldemo.R
import com.example.chimevideocalldemo.utils.Utils
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

class MeetingActivity : AppCompatActivity() {

    private val RequestPermissionResultCode = 222
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting)


        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        Dexter.withActivity(this).withPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                if (report.areAllPermissionsGranted()) {
                    // initialize and connect to the meeting- chime
                    Toast.makeText(this@MeetingActivity, "permission granted", Toast.LENGTH_LONG)
                        .show()
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

    /**
     * method: to open setting screen to grant permissions manually
     */
    private fun showSetting() {
        Utils.showAlert(this, getString(R.string.permission_req)) { isTrue ->
            if (isTrue) {
                launchSetting()
            } else {
                finish()
            }
        }
    }

    /**
     * method: to launch default setting screen on device
     */
    private fun launchSetting() {
        val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
        val uri =
            Uri.fromParts("package", this.packageName, null as String?)
        intent.data = uri
        startActivityForResult(intent, RequestPermissionResultCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RequestPermissionResultCode) {

            Toast.makeText(this@MeetingActivity, "permission granted", Toast.LENGTH_LONG)
                .show()

        }
    }
}