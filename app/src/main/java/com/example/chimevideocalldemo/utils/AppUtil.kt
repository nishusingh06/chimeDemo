package com.example.chimevideocalldemo.utils

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.chimevideocalldemo.R
import java.net.URLEncoder

class AppUtil {
    companion object {

        /* show alert dialog with - ok and cancel options & callback*/
        fun showAlert(
            context: Context,
            message: String?,
            callback: IDialogCallback?
        ) {
            showAlert(
                context,
                context.resources.getString(R.string.app_name),
                message,
                context.resources.getString(R.string.ok),
                context.resources.getString(R.string.cancel),
                callback
            )
        }

        /* show alert dialog with - custom font color of title, ok and cancel options and callback*/
        fun showAlert(
            context: Context,
            title: String,
            message: String?,
            ok: String?,
            no: String?,
            callback: IDialogCallback?
        ) {
            val dialog =
                AlertDialog.Builder(context)
                    .setTitle(Html.fromHtml("<font color='#000000'>$title</font>"))
                    .setMessage(Html.fromHtml("<font color='#737373'>$message</font>"))
                    .setPositiveButton(
                        ok
                    ) { dialog1: DialogInterface?, which: Int ->
                        callback?.onClick(true)
                    }
                    .setNegativeButton(
                        no
                    ) { dialog12: DialogInterface?, which: Int ->
                        callback?.onClick(false)
                    }.show()
            dialog.setCanceledOnTouchOutside(true)
            val positiveButton =
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton =
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setTextColor(context.getColor(R.color.color_black))
            negativeButton.setTextColor(context.getColor(R.color.color_black))
        }


        fun showToast(context: Context, msg: String) {
            Toast.makeText(
                context,
                msg,
                Toast.LENGTH_LONG
            ).show()
        }

        fun encodeURLParam(string: String?): String {
            return URLEncoder.encode(string, "utf-8")
        }

        fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false): View {
            return LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
        }
    }
}