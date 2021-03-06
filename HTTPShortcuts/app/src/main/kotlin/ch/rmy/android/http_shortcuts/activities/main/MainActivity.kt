package ch.rmy.android.http_shortcuts.activities.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color.WHITE
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import ch.rmy.android.http_shortcuts.R
import ch.rmy.android.http_shortcuts.activities.BaseActivity
import ch.rmy.android.http_shortcuts.activities.CurlImportActivity
import ch.rmy.android.http_shortcuts.activities.EditorActivity
import ch.rmy.android.http_shortcuts.activities.categories.CategoriesActivity
import ch.rmy.android.http_shortcuts.activities.editor.ShortcutEditorActivity
import ch.rmy.android.http_shortcuts.activities.settings.SettingsActivity
import ch.rmy.android.http_shortcuts.activities.variables.VariablesActivity
import ch.rmy.android.http_shortcuts.adapters.CategoryPagerAdapter
import ch.rmy.android.http_shortcuts.dialogs.ChangeLogDialog
import ch.rmy.android.http_shortcuts.dialogs.MenuDialogBuilder
import ch.rmy.android.http_shortcuts.dialogs.NetworkRestrictionWarningDialog
import ch.rmy.android.http_shortcuts.http.ExecutionScheduler
import ch.rmy.android.http_shortcuts.realm.models.Shortcut
import ch.rmy.android.http_shortcuts.utils.IntentUtil
import ch.rmy.android.http_shortcuts.utils.LauncherShortcutManager
import ch.rmy.android.http_shortcuts.utils.PromiseUtils
import ch.rmy.android.http_shortcuts.utils.SelectionMode
import ch.rmy.android.http_shortcuts.utils.attachTo
import ch.rmy.android.http_shortcuts.utils.consume
import ch.rmy.android.http_shortcuts.utils.logException
import ch.rmy.android.http_shortcuts.utils.showIfPossible
import ch.rmy.android.http_shortcuts.utils.visible
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotterknife.bindView

class MainActivity : BaseActivity(), ListFragment.TabHost {

    private val viewModel: MainViewModel by lazy {
        ViewModelProviders.of(this).get(MainViewModel::class.java)
    }

    private lateinit var adapter: CategoryPagerAdapter

    private val selectionMode by lazy {
        SelectionMode.determineMode(intent.action)
    }

    private val categories by lazy {
        viewModel.getCategories()
    }

    // Views
    private val createButton: FloatingActionButton by bindView(R.id.button_create_shortcut)
    private val viewPager: ViewPager by bindView(R.id.view_pager)
    private val tabLayout: TabLayout by bindView(R.id.tabs)

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        if (selectionMode === SelectionMode.NORMAL) {
            showStartupDialogs()
        }

        ExecutionScheduler.schedule(context)
    }

    private fun initViews() {
        createButton.setOnClickListener { showCreateOptions() }
        setupViewPager()

        tabLayout.setTabTextColors(WHITE, WHITE)
        tabLayout.setSelectedTabIndicatorColor(WHITE)

        bindViewsToViewModel()
    }

    private fun bindViewsToViewModel() {
        viewModel.appLockedSource.observe(this, Observer { isLocked ->
            createButton.visible = !isLocked
            invalidateOptionsMenu()
        })

        viewModel.getCategories().observe(this, Observer { categories ->
            tabLayout.visible = categories.size > 1
            if (viewPager.currentItem >= categories.size) {
                viewPager.currentItem = 0
            }
        })
    }

    private fun showCreateOptions() {
        MenuDialogBuilder(context)
            .title(R.string.title_create_new_shortcut_options_dialog)
            .item(R.string.button_create_new, this::openEditorForCreation)
            .item(R.string.button_curl_import, this::openCurlImport)
            .showIfPossible()
    }

    private fun openEditorForCreation() {
        val intent = ShortcutEditorActivity.IntentBuilder(context)
            .build()
        startActivityForResult(intent, REQUEST_CREATE_SHORTCUT)
    }

    private fun setupViewPager() {
        adapter = CategoryPagerAdapter(supportFragmentManager, selectionMode)
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
        viewModel.getCategories().observe(this, Observer { categories ->
            adapter.setCategories(categories)
        })
    }

    private fun showStartupDialogs() {
        val changeLog = ChangeLogDialog(context, whatsNew = true)
        if (changeLog.shouldShow()) {
            changeLog.show()
        } else {
            PromiseUtils.resolve(Unit)
        }
            .done {
                val networkWarning = NetworkRestrictionWarningDialog(context)
                if (networkWarning.shouldShow()) {
                    networkWarning.show()
                }
            }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return
        }
        when (requestCode) {
            REQUEST_CREATE_SHORTCUT -> {
                val shortcutId = intent.getLongExtra(EditorActivity.EXTRA_SHORTCUT_ID, 0)
                onShortcutCreated(shortcutId)
            }
            REQUEST_CREATE_SHORTCUT_FROM_CURL -> {
                val shortcutId = intent.getLongExtra(CurlImportActivity.EXTRA_SHORTCUT_ID, 0)
                onShortcutCreated(shortcutId)
            }
            REQUEST_SETTINGS -> {
                if (intent.getBooleanExtra(SettingsActivity.EXTRA_THEME_CHANGED, false)) {
                    recreate()
                    openSettings()
                    overridePendingTransition(0, 0)
                } else if (intent.getBooleanExtra(SettingsActivity.EXTRA_APP_LOCKED, false)) {
                    showSnackbar(R.string.message_app_locked)
                }
            }
        }
    }

    private fun onShortcutCreated(shortcutId: Long) {
        val shortcut = viewModel.getShortcutById(shortcutId) ?: return

        val currentCategory = viewPager.currentItem
        val category = if (currentCategory < categories.size) {
            val currentListFragment = adapter.getItem(currentCategory)
            val categoryId = currentListFragment.categoryId
            categories.firstOrNull { it.id == categoryId }
        } else {
            null
        }
            ?: categories.first()
        viewModel.moveShortcut(shortcut.id, targetCategoryId = category.id)
            .subscribe {
                selectShortcut(shortcut)
            }
            .attachTo(destroyer)
    }

    override fun selectShortcut(shortcut: Shortcut) {
        when (selectionMode) {
            SelectionMode.HOME_SCREEN -> returnForHomeScreen(shortcut)
            SelectionMode.PLUGIN -> returnForPlugin(shortcut)
            SelectionMode.NORMAL -> Unit
        }
    }

    private fun returnForHomeScreen(shortcut: Shortcut) {
        if (LauncherShortcutManager.supportsPinning(context)) {
            MaterialDialog.Builder(context)
                .title(R.string.title_select_placement_method)
                .content(R.string.description_select_placement_method)
                .positiveText(R.string.label_placement_method_default)
                .onPositive { _, _ ->
                    finishWithPlacement(
                        LauncherShortcutManager.createShortcutPinIntent(context, shortcut)
                    )

                }
                .negativeText(R.string.label_placement_method_legacy)
                .onNegative { _, _ ->
                    finishWithPlacement(IntentUtil.getShortcutPlacementIntent(context, shortcut, true))
                }
                .showIfPossible()
        } else {
            finishWithPlacement(IntentUtil.getShortcutPlacementIntent(context, shortcut, true))
        }
    }

    private fun finishWithPlacement(intent: Intent) {
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun returnForPlugin(shortcut: Shortcut) {
        val intent = Intent()
        intent.putExtra(EXTRA_SELECTION_ID, shortcut.id)
        intent.putExtra(EXTRA_SELECTION_NAME, shortcut.name)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override val navigateUpIcon = 0

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (viewModel.isAppLocked()) {
            menuInflater.inflate(R.menu.locked_main_activity_menu, menu)
        } else {
            menuInflater.inflate(R.menu.main_activity_menu, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> consume { openSettings() }
        R.id.action_categories -> consume { openCategoriesEditor() }
        R.id.action_variables -> consume { openVariablesEditor() }
        R.id.action_unlock -> consume { openAppUnlockDialog() }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openSettings() {
        val intent = SettingsActivity.IntentBuilder(context)
            .build()
        startActivityForResult(intent, REQUEST_SETTINGS)
    }

    private fun openCategoriesEditor() {
        val intent = CategoriesActivity.IntentBuilder(context)
            .build()
        startActivity(intent)
    }

    private fun openVariablesEditor() {
        val intent = VariablesActivity.IntentBuilder(context)
            .build()
        startActivity(intent)
    }

    private fun openAppUnlockDialog(showError: Boolean = false) {
        MaterialDialog.Builder(context)
            .title(R.string.dialog_title_unlock_app)
            .content(if (showError) R.string.dialog_text_unlock_app_retry else R.string.dialog_text_unlock_app)
            .positiveText(R.string.button_unlock_app)
            .input(null, "") { _, input ->
                unlockApp(input.toString())
            }
            .inputType(InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .negativeText(R.string.dialog_cancel)
            .showIfPossible()
    }

    private fun unlockApp(password: String) {
        viewModel.removeAppLock(password)
            .subscribe({
                if (viewModel.isAppLocked()) {
                    openAppUnlockDialog(showError = true)
                } else {
                    showSnackbar(R.string.message_app_unlocked)
                }
            }, { e ->
                showSnackbar(R.string.error_generic)
                logException(e)
            })
            .attachTo(destroyer)
    }

    private fun openCurlImport() {
        val intent = CurlImportActivity.IntentBuilder(context)
            .build()
        startActivityForResult(intent, REQUEST_CREATE_SHORTCUT_FROM_CURL)
    }

    override fun placeShortcutOnHomeScreen(shortcut: Shortcut) {
        if (LauncherShortcutManager.supportsPinning(context)) {
            LauncherShortcutManager.pinShortcut(context, shortcut)
        } else {
            sendBroadcast(IntentUtil.getShortcutPlacementIntent(context, shortcut, true))
            showSnackbar(String.format(getString(R.string.shortcut_placed), shortcut.name))
        }
    }

    override fun removeShortcutFromHomeScreen(shortcut: Shortcut) {
        sendBroadcast(IntentUtil.getShortcutPlacementIntent(context, shortcut, false))
    }

    override fun isAppLocked() = viewModel.isAppLocked()

    override fun onDestroy() {
        LauncherShortcutManager.updateAppShortcuts(context, categories)
        super.onDestroy()
    }

    companion object {

        const val EXTRA_SELECTION_ID = "ch.rmy.android.http_shortcuts.shortcut_id"
        const val EXTRA_SELECTION_NAME = "ch.rmy.android.http_shortcuts.shortcut_name"

        private const val REQUEST_CREATE_SHORTCUT = 1
        private const val REQUEST_CREATE_SHORTCUT_FROM_CURL = 2
        private const val REQUEST_SETTINGS = 3

    }
}
