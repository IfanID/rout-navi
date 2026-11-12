package com.raival.compose.file.explorer.screen.main.tab.home.data

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import kotlinx.serialization.Serializable

object HomeSectionIds {
    const val RECENT_FILES = "recent_files"
    const val CATEGORIES = "categories"
    const val STORAGE_ANALYZER = "storage_analyzer"
    const val STORAGE = "storage"
    const val BOOKMARKS = "bookmarks"
    const val PINNED_FILES = "pinned_files"
    const val RECYCLE_BIN = "recycle_bin"
    const val JUMP_TO_PATH = "jump_to_path"
}

fun getDefaultHomeLayout(minimalLayout: Boolean = false) = HomeLayout(
    listOf(
        HomeSectionConfig(
            id = HomeSectionIds.RECENT_FILES,
            type = HomeSectionType.RECENT_FILES,
            title = globalClass.getString(R.string.recent_files),
            isEnabled = !minimalLayout,
            order = 0
        ),
        HomeSectionConfig(
            id = HomeSectionIds.CATEGORIES,
            type = HomeSectionType.CATEGORIES,
            title = globalClass.getString(R.string.categories),
            isEnabled = !minimalLayout,
            order = 1
        ),
        HomeSectionConfig(
            id = HomeSectionIds.STORAGE_ANALYZER,
            type = HomeSectionType.STORAGE_ANALYZER,
            title = globalClass.getString(R.string.storage_analyzer),
            isEnabled = !minimalLayout,
            order = 2
        ),
        HomeSectionConfig(
            id = HomeSectionIds.STORAGE,
            type = HomeSectionType.STORAGE,
            title = globalClass.getString(R.string.storage),
            isEnabled = true,
            order = 3
        ),
        HomeSectionConfig(
            id = HomeSectionIds.BOOKMARKS,
            type = HomeSectionType.BOOKMARKS,
            title = globalClass.getString(R.string.bookmarks),
            isEnabled = !minimalLayout,
            order = 4
        ),
        HomeSectionConfig(
            id = HomeSectionIds.PINNED_FILES,
            type = HomeSectionType.PINNED_FILES,
            title = globalClass.getString(R.string.pinned_files),
            isEnabled = !minimalLayout,
            order = 5
        ),
        HomeSectionConfig(
            id = HomeSectionIds.RECYCLE_BIN,
            type = HomeSectionType.RECYCLE_BIN,
            title = globalClass.getString(R.string.recycle_bin),
            isEnabled = !minimalLayout,
            order = 6
        ),
        HomeSectionConfig(
            id = HomeSectionIds.JUMP_TO_PATH,
            type = HomeSectionType.JUMP_TO_PATH,
            title = globalClass.getString(R.string.jump_to_path),
            isEnabled = !minimalLayout,
            order = 7
        )
    )
)

@Serializable
data class HomeLayout(
    private val sections: List<HomeSectionConfig>
) {
    // Adds missing sections for backward compatibility with older saved layouts
    fun getSections(): List<HomeSectionConfig> {
        val merged = sections.toMutableList()

        // Add Pinned Files if missing (for layouts saved before v1.3.2)
        if (merged.find { it.id == HomeSectionIds.PINNED_FILES } == null) {
            merged.add(
                HomeSectionConfig(
                    id = HomeSectionIds.PINNED_FILES,
                    type = HomeSectionType.PINNED_FILES,
                    title = globalClass.getString(R.string.pinned_files),
                    isEnabled = true,
                    order = merged.maxOfOrNull { it.order }?.plus(1) ?: 0
                )
            )
        }

        // Layout lama belum punya Analisis Penyimpanan: sisipkan tepat sebelum
        // Penyimpanan supaya posisinya mengikuti urutan default.
        if (merged.find { it.id == HomeSectionIds.STORAGE_ANALYZER } == null) {
            val analyzer = HomeSectionConfig(
                id = HomeSectionIds.STORAGE_ANALYZER,
                type = HomeSectionType.STORAGE_ANALYZER,
                title = globalClass.getString(R.string.storage_analyzer),
                isEnabled = true,
                order = 0
            )
            val storageIndex = merged.indexOfFirst { it.id == HomeSectionIds.STORAGE }
            if (storageIndex >= 0) {
                merged.add(storageIndex, analyzer)
            } else {
                merged.add(analyzer)
            }
        }

        // Nomori ulang agar urutan tetap konsisten setelah penyisipan.
        return merged.mapIndexed { index, config -> config.copy(order = index) }
    }
}

@Serializable
data class HomeSectionConfig(
    val id: String,
    val type: HomeSectionType,
    val title: String,
    val isEnabled: Boolean,
    var order: Int
)

@Serializable
enum class HomeSectionType {
    RECENT_FILES,
    CATEGORIES,
    STORAGE_ANALYZER,
    STORAGE,
    BOOKMARKS,
    RECYCLE_BIN,
    JUMP_TO_PATH,
    PINNED_FILES
}