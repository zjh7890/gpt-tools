package com.github.zjh7890.gpttools.services

interface SessionListener {
    /**
     * 当会话列表发生变化时调用
     */
    fun sessionListChanged()
}
