package ch.rmy.android.http_shortcuts.actions.types

import android.content.Context
import android.widget.EditText
import android.widget.TextView
import ch.rmy.android.http_shortcuts.R
import ch.rmy.android.http_shortcuts.variables.VariableButton
import ch.rmy.android.http_shortcuts.variables.VariablePlaceholderProvider
import kotterknife.bindView

class ExtractCookieActionEditorView(
    context: Context,
    private val action: ExtractCookieAction,
    variablePlaceholderProvider: VariablePlaceholderProvider
) : BaseActionEditorView(context, R.layout.action_editor_extract_cookie) {

    private val cookieNameView: EditText by bindView(R.id.input_cookie_name)
    private val targetVariableView: TextView by bindView(R.id.target_variable)
    private val variableButton: VariableButton by bindView(R.id.variable_button_target_variable)

    private var selectedVariableKey: String = action.variableKey

    init {
        cookieNameView.setText(action.cookieName)

        targetVariableView.text = action.variableKey
        targetVariableView.setOnClickListener {
            variableButton.performClick()
        }
        variableButton.variablePlaceholderProvider = variablePlaceholderProvider
        variableButton.variableSource.add {
            selectedVariableKey = it.variableKey
            updateViews()
        }.attachTo(destroyer)
        updateViews()
    }

    private fun updateViews() {
        if (selectedVariableKey.isEmpty()) {
            targetVariableView.setText(R.string.action_type_target_variable_no_variable_selected)
        } else {
            targetVariableView.text = selectedVariableKey
        }
    }

    override fun compile(): Boolean {
        val cookieName = cookieNameView.text.toString()
        if (selectedVariableKey.isEmpty() || cookieName.isEmpty()) {
            return false
        }
        action.cookieName = cookieName
        action.variableKey = selectedVariableKey
        return true
    }

}