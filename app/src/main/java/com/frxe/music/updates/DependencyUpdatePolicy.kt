package com.frxe.music.updates

enum class DependencyUpdateMode {
    RuntimeUpdatable,
    RequiresAppUpdate
}

object DependencyUpdatePolicy {
    fun compiledDependencyMode():
        DependencyUpdateMode =
        DependencyUpdateMode
            .RequiresAppUpdate

    fun runtimeComponentMode():
        DependencyUpdateMode =
        DependencyUpdateMode
            .RuntimeUpdatable
}
