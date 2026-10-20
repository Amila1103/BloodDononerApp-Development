
package com.example.blooddononerapp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object StaffFeedbackDialogs {

    enum class Type {
        SUCCESS,
        ERROR,
        WARNING,
        LOGOUT,
        INFO
    }

    fun show(
        context: Context,
        type: Type,
        title: String,
        message: String,
        positiveText: String = "OK",
        negativeText: String? = null,
        cancelable: Boolean = true,
        onPositive: (() -> Unit)? = null
    ): Dialog {

        val dialog = Dialog(context)

        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        dialog.setContentView(
            R.layout.dialog_staff_feedback
        )

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)

        val iconCard =
            dialog.findViewById<MaterialCardView>(
                R.id.dialogIconCard
            )

        val icon =
            dialog.findViewById<TextView>(
                R.id.txtDialogSymbol
            )

        val titleView =
            dialog.findViewById<TextView>(
                R.id.txtDialogTitle
            )

        val messageView =
            dialog.findViewById<TextView>(
                R.id.txtDialogMessage
            )

        val positive =
            dialog.findViewById<MaterialButton>(
                R.id.btnDialogPositive
            )

        val negative =
            dialog.findViewById<MaterialButton>(
                R.id.btnDialogNegative
            )

        val accent = when (type) {

            Type.SUCCESS ->
                Color.rgb(22, 135, 85)

            Type.ERROR ->
                Color.rgb(214, 50, 65)

            Type.WARNING ->
                Color.rgb(190, 116, 19)

            Type.LOGOUT ->
                Color.rgb(239, 38, 55)

            Type.INFO ->
                Color.rgb(52, 101, 192)
        }

        val symbol = when (type) {

            Type.SUCCESS -> "✓"

            Type.ERROR -> "!"

            Type.WARNING -> "!"

            Type.LOGOUT -> "↪"

            Type.INFO -> "i"
        }

        iconCard.setCardBackgroundColor(accent)

        icon.text = symbol
        titleView.text = title
        messageView.text = message

        positive.text = positiveText

        positive.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                accent
            )

        positive.setTextColor(Color.WHITE)

        if (negativeText.isNullOrBlank()) {

            negative.visibility = View.GONE

        } else {

            negative.visibility = View.VISIBLE
            negative.text = negativeText

            negative.setOnClickListener {
                dialog.dismiss()
            }
        }

        positive.setOnClickListener {

            dialog.dismiss()

            onPositive?.invoke()
        }

        dialog.show()

        val window = dialog.window

        if (window != null) {

            val width =
                (
                        context.resources.displayMetrics.widthPixels *
                                0.90f
                        ).toInt()

            window.setLayout(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT
            )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            val params = window.attributes

            params.dimAmount = 0.45f

            window.attributes = params
        }

        return dialog
    }
}
