package ch.rmy.android.http_shortcuts.actions.types

import android.content.Context
import ch.rmy.android.http_shortcuts.R
import ch.rmy.android.http_shortcuts.realm.Controller
import ch.rmy.android.http_shortcuts.utils.focus
import ch.rmy.android.http_shortcuts.variables.VariableButton
import ch.rmy.android.http_shortcuts.variables.VariableEditText
import kotterknife.bindView

class RenameShortcutActionEditorView(context: Context, private val action: RenameShortcutAction) : BaseActionEditorView(context, R.layout.action_editor_rename_shortcut) {

    private val newNameView: VariableEditText by bindView(R.id.new_shortcut_name)
    private val variableButton: VariableButton by bindView(R.id.variable_button_new_shortcut_name)

    private val variables by lazy {
        destroyer.own(Controller()).getVariables()
    }

    init {
        newNameView.bind(variableButton, variables)
        newNameView.rawString = action.name
        newNameView.focus()
    }

    override fun compile(): Boolean {
        val newName = newNameView.rawString
        if (newName.isEmpty()) {
            newNameView.error = context.getString(R.string.validation_name_not_empty)
            newNameView.focus()
            return false
        }
        action.name = newName
        return true
    }

}