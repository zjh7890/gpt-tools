package com.github.zjh7890.gpttools.annotation

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.collect.Maps
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.NotNull
import java.util.*

/**
 * Mybatis 相关注解定义
 *
 * @author yanglin
 * @since 2018-07-30
 */
class Annotation private constructor(
    @NotNull val label: String,
    @NotNull val qualifiedName: String
) : Cloneable {

    private var attributePairs: MutableMap<String, AnnotationValue> = Maps.newHashMap()

    private fun addAttribute(key: String, value: AnnotationValue): Annotation {
        this.attributePairs[key] = value
        return this
    }

    fun withAttribute(@NotNull key: String, @NotNull value: AnnotationValue): Annotation {
        val copy = this.clone()
        copy.attributePairs = Maps.newHashMap(this.attributePairs)
        return copy.addAttribute(key, value)
    }

    fun withValue(@NotNull value: AnnotationValue): Annotation {
        return withAttribute("value", value)
    }

    override fun toString(): String {
        val builder = StringBuilder(label)
        if (attributePairs.isNotEmpty()) {
            builder.append(setupAttributeText())
        }
        return builder.toString()
    }

    private fun setupAttributeText(): String {
        val singleValue = getSingleValue()
        return singleValue.orElseGet { getComplexValue() }
    }

    private fun getComplexValue(): String {
        val builder = StringBuilder("(")
        for (key in attributePairs.keys) {
            builder.append(key)
            builder.append(" = ")
            builder.append(attributePairs[key].toString())
            builder.append(", ")
        }
        builder.deleteCharAt(builder.length - 2)
        builder.deleteCharAt(builder.length - 1)
        builder.append(")")
        return builder.toString()
    }

    fun toPsiClass(@NotNull project: Project): Optional<PsiClass> {
        return Optional.ofNullable(
            JavaPsiFacade.getInstance(project).findClass(
                qualifiedName, GlobalSearchScope.allScope(project)
            )
        )
    }

    private fun getSingleValue(): Optional<String> {
        return try {
            val value = Iterables.getOnlyElement(attributePairs.keys)
            val builder = "(" + attributePairs[value].toString() + ")"
            Optional.of(builder)
        } catch (e: Exception) {
            Optional.empty()
        }
    }

    override fun clone(): Annotation {
        return try {
            super.clone() as Annotation
        } catch (e: CloneNotSupportedException) {
            throw IllegalStateException()
        }
    }

    interface AnnotationValue

    class StringValue(@NotNull private val value: String) : AnnotationValue {
        override fun toString(): String {
            return "\"$value\""
        }
    }

    companion object {
        val PARAM = Annotation("@Param", "org.apache.ibatis.annotations.Param")
        val SELECT = Annotation("@Select", "org.apache.ibatis.annotations.Select")
        val UPDATE = Annotation("@Update", "org.apache.ibatis.annotations.Update")
        val INSERT = Annotation("@Insert", "org.apache.ibatis.annotations.Insert")
        val DELETE = Annotation("@Delete", "org.apache.ibatis.annotations.Delete")
        val ALIAS = Annotation("@Alias", "org.apache.ibatis.type.Alias")
        val AUTOWIRED = Annotation("@Autowired", "org.springframework.beans.factory.annotation.Autowired")
        val RESOURCE = Annotation("@Resource", "javax.annotation.Resource")
        val STATEMENT_SYMMETRIES: Set<Annotation> = ImmutableSet.of(SELECT, UPDATE, INSERT, DELETE)
    }
}