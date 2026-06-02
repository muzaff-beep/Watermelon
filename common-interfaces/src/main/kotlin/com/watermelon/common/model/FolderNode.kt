package com.watermelon.common.model

/** A node in the folder tree built from the MediaStore Phase-1 sweep. */
data class FolderNode(
    val path: String,
    val displayName: String,
    val itemCount: Int,
    val children: List<FolderNode>
)
