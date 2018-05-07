package ch.rmy.android.http_shortcuts.actions.types

import android.content.Context
import ch.rmy.android.http_shortcuts.R
import ch.rmy.android.http_shortcuts.realm.Controller
import ch.rmy.android.http_shortcuts.utils.focus
import ch.rmy.android.http_shortcuts.variables.VariableButton
import ch.rmy.android.http_shortcuts.variables.VariableEditText
import kotterknife.bindView

class ToastActionEditorView(context: Context, private val action: ToastAction) : BaseActionEditorView(context, R.layout.action_editor_show_toast) {

    private val messageView: VariableEditText by bindView(R.id.toast_message)
    private val variableButton: VariableButton by bindView(R.id.variable_button_toast_message)

    private val variables by lazy {
        destroyer.own(Controller()).getVariables()
    }

    init {
        messageView.bind(variableButton, variables)
        messageView.rawString = action.message
        messageView.focus()
    }

    override fun compile(): Boolean {
        action.message = messageView.rawString
        return true
    }

}