package com.github.zjh7890.gpttools.utils

import org.w3c.dom.Element
import java.net.URLDecoder
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

object DrawioToMermaidConverter {
    private val idMap = mutableMapOf<String, Int>()
    private val reverseIdMap = mutableMapOf<Int, String>()
    private var idCounter = 1  // Start counting from 1 for mermaid nodes
    private val vertices = mutableMapOf<String, Vertex>()
    private val edges = mutableListOf<Edge>()
    private val incomingEdges = mutableMapOf<String, MutableList<String>>()
    private val visitedEdges = mutableListOf<Edge>()

    fun convert(encodedData: String): String {
        // Decode the URL-encoded XML data
        val decodedData = URLDecoder.decode(encodedData, "UTF-8")

        // Parse the XML
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(decodedData.byteInputStream())
        doc.documentElement.normalize()

        val rootElement = doc.documentElement
        val mxCells = rootElement.getElementsByTagName("mxCell")

        // Process nodes and edges
        processCells(mxCells)

        // Find roots and perform BFS
        val bfsResult = performBFS()

        return constructGraph(bfsResult)
    }

    private fun processCells(mxCells: org.w3c.dom.NodeList) {
        val edgeLabels = mutableMapOf<String, String>() // 存储边ID与标签的映射

        // 首先收集所有的标签
        for (i in 0 until mxCells.length) {
            val cell = mxCells.item(i) as Element
            if (cell.getAttribute("style").contains("edgeLabel")) {
                // 这是一个边的标签
                val parentEdgeId = cell.getAttribute("parent")
                val labelValue = cell.getAttribute("value")
                edgeLabels[parentEdgeId] = labelValue // 将标签值与边的ID关联
            }
        }

        // 然后处理节点和边
        for (i in 0 until mxCells.length) {
            val cell = mxCells.item(i) as Element
            if (cell.hasAttribute("vertex") && cell.getAttribute("vertex") == "1" && !cell.getAttribute("style").startsWith("edgeLabel")) {
                val id = cell.getAttribute("id")
                val value = cell.getAttribute("value").replace("\n", " ").replace("\"", "")
                val style = cell.getAttribute("style")
                val type = if (style.contains("rhombus")) NodeType.DECISION else NodeType.NORMAL
                vertices[id] = Vertex(id, value, type)
                incomingEdges.putIfAbsent(id, mutableListOf())
            } else if (cell.hasAttribute("edge") && cell.getAttribute("edge") == "1") {
                val source = cell.getAttribute("source")
                val target = cell.getAttribute("target")
                val edgeId = cell.getAttribute("id")
                val label = edgeLabels[edgeId] ?: "" // 从映射中获取标签，如果没有则为空字符串
                edges.add(Edge(source, target, label))
                incomingEdges.computeIfAbsent(target) { mutableListOf() }.add(source)
            }
        }
    }

    private fun constructGraph(visitOrder: List<String>): String {
        val sb = StringBuilder("graph TD\n")
        visitOrder.forEach { nodeId ->
            val vertex = vertices[nodeId]
            when (vertex!!.type) {
                NodeType.DECISION -> sb.append("${idMap[nodeId]}{\"${vertex.value}\"}\n")  // 菱形节点用花括号标识
                else -> sb.append("${idMap[nodeId]}[\"${vertex.value}\"]\n")  // 默认方形节点
            }
        }
        visitedEdges.forEach { edge ->
            val sourceId = idMap[edge.source]!!
            val targetId = idMap[edge.target]!!
            val label = if (edge.label.isNotEmpty()) "|${edge.label}|" else ""  // 添加边的标签
            sb.append("$sourceId --> $label $targetId\n")
        }
        return sb.toString()
    }


    private fun performBFS(): List<String> {
        val queue: Queue<String> = LinkedList()
        val visitedOrder = mutableListOf<String>()

        // Enqueue all roots
        vertices.keys.filter { incomingEdges[it]?.isEmpty() == true }.forEach { queue.add(it) }

        while (queue.isNotEmpty()) {
            val currentId = queue.poll()
            if (!idMap.containsKey(currentId)) {
                val currentIdNumber = idCounter++
                idMap[currentId] = currentIdNumber
                reverseIdMap[currentIdNumber] = currentId
                visitedOrder.add(currentId)
            }
            edges.filter { it.source == currentId }.forEach { edge ->
                if (!idMap.containsKey(edge.target)) {
                    queue.add(edge.target)
                }
                if (!visitedEdges.contains(edge)) {
                    visitedEdges.add(edge) // Mark edge as visited
                }
            }
        }

        return visitedOrder
    }

    data class Vertex(val id: String, val value: String, val type: NodeType = NodeType.NORMAL)
    data class Edge(val source: String, val target: String, val label: String = "")
}

enum class NodeType {
    NORMAL, DECISION, OTHER  // 可以根据需要添加更多类型
}
