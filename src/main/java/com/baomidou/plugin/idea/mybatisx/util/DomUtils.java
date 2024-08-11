package com.baomidou.plugin.idea.mybatisx.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The type Dom utils.
 */
public final class DomUtils {

    private DomUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Find dom elements collection.
     *
     * @param <T>     the type parameter
     * @param project the project
     * @param clazz   the clazz
     * @return the collection
     */
    @NotNull
    @NonNls
    public static <T extends DomElement> Collection<T> findDomElements(@NotNull Project project, Class<T> clazz) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        List<DomFileElement<T>> elements = DomService.getInstance().getFileElements(clazz, project, scope);
        return elements.stream().map(DomFileElement::getRootElement).collect(Collectors.toList());
    }

    /**
     * <p>
     * 判断是否为 Mybatis XML 文件
     * </p>
     *
     * @param file 判断文件
     * @return boolean
     */
    public static boolean isMybatisFile(@Nullable PsiFile file) {
        Boolean mybatisFile = null;
        if (file == null) {
            mybatisFile = false;
        }
        if (mybatisFile == null) {
            if (!isXmlFile(file)) {
                mybatisFile = false;
            }
        }
        if (mybatisFile == null) {
            XmlTag rootTag = ((XmlFile) file).getRootTag();
            if (rootTag == null) {
                mybatisFile = false;
            }
            if (mybatisFile == null) {
                if (!"mapper".equals(rootTag.getName())) {
                    mybatisFile = false;
                }
            }
        }
        if (mybatisFile == null) {
            mybatisFile = true;
        }
        return mybatisFile;
    }

    /**
     * Is mybatis configuration file boolean.
     *
     * @param file the file
     * @return the boolean
     */
    public static boolean isMybatisConfigurationFile(@NotNull PsiFile file) {
        if (!isXmlFile(file)) {
            return false;
        }
        XmlTag rootTag = ((XmlFile) file).getRootTag();
        return null != rootTag && "configuration".equals(rootTag.getName());
    }

    /**
     * Is beans file boolean.
     *
     * @param file the file
     * @return the boolean
     */
    public static boolean isBeansFile(@NotNull PsiFile file) {
        if (!isXmlFile(file)) {
            return false;
        }
        XmlTag rootTag = ((XmlFile) file).getRootTag();
        return null != rootTag && "beans".equals(rootTag.getName());
    }

    /**
     * Is xml file boolean.
     *
     * @param file the file
     * @return the boolean
     */
    static boolean isXmlFile(@NotNull PsiFile file) {
        return file instanceof XmlFile;
    }

}
