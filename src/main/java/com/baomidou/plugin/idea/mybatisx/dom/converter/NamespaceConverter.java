package com.baomidou.plugin.idea.mybatisx.dom.converter;

import com.baomidou.plugin.idea.mybatisx.util.JavaUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 加入命名空间检查, 当mapper.xml和mapper.class不匹配时,
 * 对mapper.xml的namespace标签标记红色下划线
 */
public class NamespaceConverter extends ConverterAdaptor<PsiClass> {
    /**
     * 转换成类
     *
     * @param id
     * @param context
     * @return
     */
    @Nullable
    @Override
    public PsiClass fromString(@Nullable @NonNls String id, ConvertContext context) {
        if (StringUtils.isEmpty(id)) {
            return null;
        }
        Optional<PsiClass> clazz = JavaUtils.findClazz(context.getProject(), id);
        return clazz.orElse(null);
    }

    @Override
    public void bindReference(GenericDomValue<PsiClass> genericValue, ConvertContext context, PsiElement newTarget) {
        if (newTarget instanceof PsiClass) {
            final PsiClass psiClass = (PsiClass) newTarget;
            final String qualifiedName = psiClass.getQualifiedName();
            genericValue.setStringValue(qualifiedName);
        }
    }
}
