package org.stepic.droid.util

import android.content.Context
import android.support.design.widget.Snackbar
import android.view.View
import org.stepic.droid.R

object SnackbarShower {
    fun showInternetRetrySnackbar(rootView: View, context: Context, listener: View.OnClickListener) {
        Snackbar.make(rootView, R.string.internet_problem_short, duration5Sec)
                .setAction(R.string.retry_internet, listener)
                .setActionTextColor(ColorUtil.getColorArgb(R.color.snack_action_color, context))
                .setTextColor(
                        ColorUtil.getColorArgb(R.color.white,
                                context))
                .show()
    }


    fun showTurnOnDownloadingInSettings(rootView: View, context: Context, listener: View.OnClickListener) {
        Snackbar.make(rootView, R.string.allow_mobile_snack, duration5Sec)
                .setAction(R.string.settings_title, listener)
                .setActionTextColor(ColorUtil.getColorArgb(R.color.snack_action_color, context))
                .setTextColor(
                        ColorUtil.getColorArgb(R.color.white,
                                context))
                .show()
    }
}
