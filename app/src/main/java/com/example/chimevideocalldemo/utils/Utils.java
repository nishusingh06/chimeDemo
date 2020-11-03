package com.example.chimevideocalldemo.utils;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import com.example.chimevideocalldemo.R;

public class Utils {

    /* show alert dialog with - ok and cancel options & callback*/
    public static void showAlert(Context context, String message, IDialogCallback callback) {
        showAlert(context, context.getResources().getString(R.string.app_name), message, context.getResources().getString(R.string.ok), context.getResources().getString(R.string.cancel), callback);
    }

    /* show alert dialog with - custom font color of title, ok and cancel options and callback*/
    public static void showAlert(Context context, String title, String message, String ok, String no, final IDialogCallback callback) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(Html.fromHtml("<font color='#000000'>" + title + "</font>"))
                .setMessage(Html.fromHtml("<font color='#737373'>" + message + "</font>"))
                .setPositiveButton(ok, (dialog1, which) -> {
                    if (callback != null) {
                        callback.onClick(true);
                    }
                })
                .setNegativeButton(no, (dialog12, which) -> {
                    if (callback != null) {
                        callback.onClick(false);
                    }
                }).show();

        dialog.setCanceledOnTouchOutside(true);
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        positiveButton.setTextColor(context.getColor(R.color.color_black));
        negativeButton.setTextColor(context.getColor(R.color.color_black));
    }

    public static String[] splitName(String text) {

        if (!TextUtils.isEmpty(text)) {
            if (text.contains(" ")) {

                String[] splited = text.split(" ");
                return splited;
            } else
                return null;
        }
        return null;
    }
}
