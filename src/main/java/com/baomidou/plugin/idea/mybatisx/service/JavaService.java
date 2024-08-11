package com.baomidou.plugin.idea.mybatisx.service;

import com.baomidou.plugin.idea.mybatisx.dom.model.IdDomElement;
import com.baomidou.plugin.idea.mybatisx.dom.model.Mapper;
import com.baomidou.plugin.idea.mybatisx.util.MapperUtils;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The type Java service.
 *
 * @author yanglin
 */
@Service(value = Service.Level.PROJECT)
final public class JavaService implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Project project;

    private final JavaPsiFacade javaPsiFacade;

    /**
     * Instantiates a new Java service.
     *
     * @param project the project
     */
    public JavaService(Project project) {
        this.project = project;
        this.javaPsiFacade = JavaPsiFacade.getInstance(project);
    }

    /**
     * Gets instance.
     *
     * @param project the project
     * @return the instance
     */
    public static JavaService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, JavaService.class);
    }

    /**
     * Gets reference clazz of psi field.
     *
     * @param field the field
     * @return the reference clazz of psi field
     */
    public Optional<PsiClass> getReferenceClazzOfPsiField(@NotNull PsiElement field) {
        if (!(field instanceof PsiField)) {
            return Optional.empty();
        }
        PsiType type = ((PsiField) field).getType();
        return type instanceof PsiClassReferenceType ? Optional.ofNullable(((PsiClassReferenceType) type).resolve()) :
            Optional.empty();
    }

    /**
     * Find statement optional.
     *
     * @param method the method
     * @return the optional
     */
    public Optional<DomElement> findStatement(@NotNull PsiMethod method) {
        CommonProcessors.FindFirstProcessor<IdDomElement> processor = new CommonProcessors.FindFirstProcessor<>();
        processMethod(method, processor);
        return processor.isFound() ? Optional.ofNullable(processor.getFoundValue()) : Optional.empty();
    }

    /**
     * Process.
     *
     * @param psiMethod the psi method
     * @param processor the processor
     */
    public void processMethod(@NotNull PsiMethod psiMethod, @NotNull Processor<IdDomElement> processor) {
        PsiClass psiClass = psiMethod.getContainingClass();
        if (null == psiClass) {
            return;
        }
        Collection<Mapper> mappers = MapperUtils.findMappers(psiMethod.getProject());

        Set<String> ids = new HashSet<>();
        String id = psiClass.getQualifiedName() + "." + psiMethod.getName();
        ids.add(id);
        final Query<PsiClass> search = ClassInheritorsSearch.search(psiClass);
        final Collection<PsiClass> allChildren = search.findAll();

        for (PsiClass psiElement : allChildren) {
            String childId = psiElement.getQualifiedName() + "." + psiMethod.getName();
            ids.add(childId);
        }

        mappers.stream()
            .flatMap(mapper -> mapper.getDaoElements().stream())
            .filter(idDom -> ids.contains(MapperUtils.getIdSignature(idDom)))
            .forEach(processor::process);

    }


    /**
     * Process.
     *
     * @param clazz     the clazz
     * @param processor the processor
     */
    @SuppressWarnings("unchecked")
    public void processClass(@NotNull PsiClass clazz, @NotNull Processor<Mapper> processor) {
        String ns = clazz.getQualifiedName();
        for (Mapper mapper : MapperUtils.findMappers(clazz.getProject())) {
            if (MapperUtils.getNamespace(mapper).equals(ns)) {
                processor.process(mapper);
            }
        }
    }


    /**
     * Find with find first processor optional.
     *
     * @param target the target
     * @return the optional
     */
    public Optional<Mapper> findWithFindFirstProcessor(@NotNull PsiClass target) {
        CommonProcessors.FindFirstProcessor<Mapper> processor = new CommonProcessors.FindFirstProcessor<>();
        processClass(target, processor);
        return Optional.ofNullable(processor.getFoundValue());
    }
}
