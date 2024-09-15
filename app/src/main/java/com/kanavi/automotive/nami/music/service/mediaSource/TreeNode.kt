package com.kanavi.automotive.nami.music.service.mediaSource

import com.kanavi.automotive.nami.music.common.constant.Constants.isFrontDisplay
import java.util.concurrent.CopyOnWriteArrayList

data class TreeNode(
    var value: String = "",
    var children: CopyOnWriteArrayList<TreeNode> = CopyOnWriteArrayList()
) {
    fun addChild(node: TreeNode) {
        children.add(node)
    }

    companion object {
        fun createTree(
            pathList: List<String>,
            root: TreeNode,
        ): TreeNode {
            pathList.forEach { path ->
                var delimiter = if (isFrontDisplay()) "/" else "\\"
                val pathSegments = path.split(delimiter)
                var currentNode = root
                pathSegments.forEachIndexed { index, _ ->
                    val segment = pathSegments.slice(0..index).joinToString(delimiter)
                    val children = currentNode.children
                    val node = children.find { it.value == segment }
                    currentNode = if (node == null) {
                        val newNode = TreeNode(segment)
                        currentNode.addChild(newNode)
                        newNode
                    } else {
                        node
                    }
                }
            }
            return root
        }

        fun findNode(root: TreeNode, path: String): TreeNode? {
            if (root.value == path) {
                return root.copy()
            }
            root.children.forEach {
                val result = findNode(it, path)
                if (result != null) {
                    return result.copy()
                }
            }
            return null
        }
    }
}