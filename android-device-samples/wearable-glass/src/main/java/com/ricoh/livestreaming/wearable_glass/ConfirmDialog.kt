/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.wearable_glass

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class ConfirmDialog : DialogFragment() {
    var onPositiveButtonClickListener: DialogInterface.OnClickListener? = null
    var onNegativeButtonClickListener: DialogInterface.OnClickListener? = null
    var onCancelListener: DialogInterface.OnCancelListener? = null
    var onDismissListener: DialogInterface.OnDismissListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.apply {
            setTitle(arguments?.getString("title"))
            setMessage(arguments?.getString("message"))
            setPositiveButton(arguments?.getString("positiveButtonText"), onPositiveButtonClickListener)
            setNegativeButton(arguments?.getString("negativeButtonText"), onNegativeButtonClickListener)
            setOnCancelListener(onCancelListener)
            setOnDismissListener(onDismissListener)
        }

        return builder.create()
    }
}
