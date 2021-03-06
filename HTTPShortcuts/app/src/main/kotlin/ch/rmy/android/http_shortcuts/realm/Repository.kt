package ch.rmy.android.http_shortcuts.realm

import ch.rmy.android.http_shortcuts.realm.models.AppLock
import ch.rmy.android.http_shortcuts.realm.models.Base
import ch.rmy.android.http_shortcuts.realm.models.Category
import ch.rmy.android.http_shortcuts.realm.models.HasId
import ch.rmy.android.http_shortcuts.realm.models.PendingExecution
import ch.rmy.android.http_shortcuts.realm.models.Shortcut
import ch.rmy.android.http_shortcuts.realm.models.Variable
import ch.rmy.android.http_shortcuts.utils.UUIDUtils.newUUID
import io.realm.Case
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.kotlin.where

object Repository {

    internal fun getBase(realm: Realm): Base? =
        realm
            .where<Base>()
            .findFirst()

    internal fun getShortcuts(realm: Realm): Collection<Shortcut> =
        realm
            .where<Shortcut>()
            .notEqualTo(HasId.FIELD_ID, Shortcut.TEMPORARY_ID)
            .findAll()

    internal fun getCategoryById(realm: Realm, categoryId: Long): Category? =
        realm
            .where<Category>()
            .equalTo(HasId.FIELD_ID, categoryId)
            .findFirst()

    internal fun getCategoryByIdAsync(realm: Realm, categoryId: Long): Category =
        realm
            .where<Category>()
            .equalTo(HasId.FIELD_ID, categoryId)
            .findFirstAsync()

    internal fun getShortcutById(realm: Realm, shortcutId: Long): Shortcut? =
        realm
            .where<Shortcut>()
            .equalTo(HasId.FIELD_ID, shortcutId)
            .findFirst()

    internal fun getShortcutByName(realm: Realm, shortcutName: String): Shortcut? =
        realm
            .where<Shortcut>()
            .equalTo(Shortcut.FIELD_NAME, shortcutName, Case.INSENSITIVE)
            .findFirst()

    internal fun getVariableById(realm: Realm, variableId: Long): Variable? =
        realm
            .where<Variable>()
            .equalTo(HasId.FIELD_ID, variableId)
            .findFirst()

    internal fun getVariableByKey(realm: Realm, key: String): Variable? =
        realm
            .where<Variable>()
            .equalTo(Variable.FIELD_KEY, key)
            .findFirst()

    internal fun getShortcutsPendingExecution(realm: Realm): RealmResults<PendingExecution> =
        realm
            .where<PendingExecution>()
            .sort(PendingExecution.FIELD_ENQUEUED_AT)
            .findAll()

    internal fun getShortcutPendingExecution(realm: Realm, shortcutId: Long): PendingExecution? =
        realm
            .where<PendingExecution>()
            .equalTo(PendingExecution.FIELD_SHORTCUT_ID, shortcutId)
            .findFirst()

    internal fun getAppLock(realm: Realm): AppLock? =
        realm
            .where<AppLock>()
            .findFirst()

    internal fun deleteShortcut(realm: Realm, shortcutId: Long) {
        getShortcutById(realm, shortcutId)?.apply {
            headers.deleteAllFromRealm()
            parameters.deleteAllFromRealm()
            deleteFromRealm()
        }
    }

    internal fun copyShortcut(realm: Realm, sourceShortcut: Shortcut, targetShortcutId: Long): Shortcut =
        sourceShortcut.detachFromRealm()
            .apply {
                id = targetShortcutId
                parameters.forEach { parameter ->
                    parameter.id = newUUID()
                }
                headers.forEach { header ->
                    header.id = newUUID()
                }
            }
            .let {
                realm.copyToRealm(it)
            }

    internal fun generateId(realm: Realm, clazz: Class<out RealmObject>): Long {
        val maxId = realm.where(clazz).max(HasId.FIELD_ID)
        val maxIdLong = Math.max(maxId?.toLong() ?: 0, 0)
        return maxIdLong + 1
    }

    internal fun moveShortcut(realm: Realm, shortcutId: Long, targetPosition: Int? = null, targetCategoryId: Long? = null) {
        val shortcut = Repository.getShortcutById(realm, shortcutId) ?: return
        val categories = Repository.getBase(realm)?.categories ?: return
        val targetCategory = if (targetCategoryId != null) {
            Repository.getCategoryById(realm, targetCategoryId)
        } else {
            categories.first { category -> category.shortcuts.any { it.id == shortcutId } }
        } ?: return

        for (category in categories) {
            category.shortcuts.remove(shortcut)
        }
        if (targetPosition != null) {
            targetCategory.shortcuts.add(targetPosition, shortcut)
        } else {
            targetCategory.shortcuts.add(shortcut)
        }
    }

}