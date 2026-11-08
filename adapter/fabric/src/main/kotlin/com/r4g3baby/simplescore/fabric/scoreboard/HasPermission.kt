package com.r4g3baby.simplescore.fabric.scoreboard

import com.r4g3baby.simplescore.api.scoreboard.VarReplacer
import com.r4g3baby.simplescore.api.scoreboard.condition.Condition

data class HasPermission<V : Any>(
    override val name: String,
    val permission: String, val parsePermission: Boolean, val hasPermission: (V, String) -> Boolean
) : Condition<V> {
    override fun check(viewer: V, varReplacer: VarReplacer<V>): Boolean {
        val perm = if (parsePermission) varReplacer.replace(permission, viewer) else permission
        return hasPermission(viewer, perm)
    }
}
