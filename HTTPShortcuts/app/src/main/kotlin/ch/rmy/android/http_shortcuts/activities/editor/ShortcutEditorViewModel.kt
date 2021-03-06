package ch.rmy.android.http_shortcuts.activities.editor

import android.app.Application
import androidx.lifecycle.LiveData
import ch.rmy.android.http_shortcuts.realm.RealmViewModel
import ch.rmy.android.http_shortcuts.realm.Repository
import ch.rmy.android.http_shortcuts.realm.commitAsync
import ch.rmy.android.http_shortcuts.realm.models.HasId.Companion.FIELD_ID
import ch.rmy.android.http_shortcuts.realm.models.Shortcut
import ch.rmy.android.http_shortcuts.realm.models.Shortcut.Companion.TEMPORARY_ID
import ch.rmy.android.http_shortcuts.realm.toLiveData
import io.reactivex.Completable
import io.realm.kotlin.where

class ShortcutEditorViewModel(application: Application) : RealmViewModel(application) {

    fun init(shortcutId: Long?): Completable {
        this.shortcutId = shortcutId
        return persistedRealm.commitAsync { realm ->
            Repository.deleteShortcut(realm, TEMPORARY_ID)
            if (shortcutId == null) {
                realm.copyToRealmOrUpdate(Shortcut.createNew(id = TEMPORARY_ID))
            } else {
                Repository.copyShortcut(realm, Repository.getShortcutById(realm, shortcutId)!!, TEMPORARY_ID)
            }
        }
            .doOnComplete {
                isInitialized = true
            }
    }

    var isInitialized: Boolean = false
        private set

    private var shortcutId: Long? = null

    val shortcut: LiveData<Shortcut?>
        get() = persistedRealm
            .where<Shortcut>()
            .equalTo(FIELD_ID, TEMPORARY_ID)
            .findFirstAsync()
            .toLiveData()

    fun hasChanges(): Boolean {
        val oldShortcut = shortcutId
            ?.let { Repository.getShortcutById(persistedRealm, it)!! }
            ?: Shortcut.createNew()
        val newShortcut = getShortcut()
        return !newShortcut.isSameAs(oldShortcut)
    }

    fun updateShortcut(name: String, description: String): Completable =
        persistedRealm.commitAsync {
            getShortcut().apply {
                this.name = name
                this.description = description
            }
        }

    private fun getShortcut(): Shortcut =
        Repository.getShortcutById(persistedRealm, TEMPORARY_ID)!!

    fun trySave(): Completable {
        // TODO: Validate, abort if invalid

        return persistedRealm.commitAsync { realm ->
            val id = shortcutId ?: Repository.generateId(realm, Shortcut::class.java)

            shortcutId?.let { shortcutId ->
                Repository.deleteShortcut(realm, shortcutId)
            }

            val shortcut = Repository.getShortcutById(realm, TEMPORARY_ID)!!
            Repository.copyShortcut(realm, shortcut, id)

            Repository.deleteShortcut(realm, TEMPORARY_ID)
        }
    }

}