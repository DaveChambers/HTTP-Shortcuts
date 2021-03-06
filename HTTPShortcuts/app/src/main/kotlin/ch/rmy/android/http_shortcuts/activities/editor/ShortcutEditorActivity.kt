package ch.rmy.android.http_shortcuts.activities.editor

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import ch.rmy.android.http_shortcuts.R
import ch.rmy.android.http_shortcuts.activities.BaseActivity
import ch.rmy.android.http_shortcuts.realm.models.Shortcut
import ch.rmy.android.http_shortcuts.utils.BaseIntentBuilder
import ch.rmy.android.http_shortcuts.utils.attachTo
import ch.rmy.android.http_shortcuts.utils.consume
import ch.rmy.android.http_shortcuts.utils.logException
import ch.rmy.android.http_shortcuts.utils.showIfPossible
import ch.rmy.android.http_shortcuts.utils.showToast
import com.afollestad.materialdialogs.MaterialDialog
import kotterknife.bindView

class ShortcutEditorActivity : BaseActivity() {

    private val shortcutId by lazy {
        intent.getLongExtra(EXTRA_SHORTCUT_ID, 0L).takeUnless { it == 0L }
    }

    private val viewModel: ShortcutEditorViewModel by lazy {
        ViewModelProviders.of(this).get(ShortcutEditorViewModel::class.java)
    }

    // Views
    private val nameView: EditText by bindView(R.id.input_shortcut_name)
    private val descriptionView: EditText by bindView(R.id.input_description)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(if (shortcutId != null) {
            R.string.edit_shortcut
        } else {
            R.string.create_shortcut
        })
        setContentView(R.layout.activity_loading)
        initViewModel()
    }

    private fun initViewModel() {
        viewModel.init(shortcutId)
            .subscribe({
                initViews()
            }, { e ->
                logException(e)
                showToast(R.string.error_generic)
                finish()
            })
            .attachTo(destroyer)
    }

    private fun initViews() {
        setContentView(R.layout.activity_shortcut_editor_overview)
        invalidateOptionsMenu()
        bindViewsToViewModel()
        bindClickListeners()
    }

    private fun bindViewsToViewModel() {
        viewModel.shortcut.observe(this, Observer {
            it?.let(this::updateShortcutViews)
        })
    }

    private fun updateShortcutViews(shortcut: Shortcut) {
        nameView.setText(shortcut.name)
        descriptionView.setText(shortcut.description)
    }

    private fun bindClickListeners() {

    }

    override val navigateUpIcon = R.drawable.ic_clear

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (viewModel.isInitialized) {
            menuInflater.inflate(R.menu.editor_activity_menu, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> consume { onCloseEditor() }
        R.id.action_save_shortcut -> consume { trySaveShortcut() }
        R.id.action_test_shortcut -> consume { testShortcut() }
        else -> super.onOptionsItemSelected(item)
    }

    private fun onCloseEditor() {
        if (viewModel.isInitialized && viewModel.hasChanges()) {
            MaterialDialog.Builder(context)
                .content(R.string.confirm_discard_changes_message)
                .positiveText(R.string.dialog_discard)
                .onPositive { _, _ -> cancelAndClose() }
                .negativeText(R.string.dialog_cancel)
                .showIfPossible()
        } else {
            cancelAndClose()
        }
    }

    private fun cancelAndClose() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun trySaveShortcut() {
        updateViewModelFromViews()

        viewModel.trySave()
            .subscribe {
                finish()
            }
            .attachTo(destroyer)
    }

    private fun testShortcut() {
        updateViewModelFromViews()
        // TODO
    }

    private fun updateViewModelFromViews() {
        viewModel.updateShortcut(
            name = nameView.text.toString(),
            description = descriptionView.text.toString()
        )
    }

    override fun onBackPressed() {
        onCloseEditor()
    }

    class IntentBuilder(context: Context) : BaseIntentBuilder(context, ShortcutEditorActivity::class.java) {

        fun shortcutId(shortcutId: Long) = this.also {
            intent.putExtra(EXTRA_SHORTCUT_ID, shortcutId)
        }

    }

    companion object {

        private const val EXTRA_SHORTCUT_ID = "shortcutId"

    }

}