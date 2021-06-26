/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.extension.extractor;

import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.Group;
import com.djrapitops.plan.extension.annotation.*;
import com.djrapitops.plan.extension.builder.ExtensionDataBuilder;
import com.djrapitops.plan.extension.table.Table;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation detail, for extracting methods from {@link com.djrapitops.plan.extension.DataExtension}.
 * <p>
 * This class can be used for testing validity of annotation implementations
 * in your unit tests to avoid runtime errors. {@link ExtensionExtractor#validateAnnotations()}
 *
 * @author AuroraLS3
 */
public final class ExtensionExtractor {

    private final DataExtension extension;
    private final String extensionName;

    private final List<String> warnings = new ArrayList<>();

    private PluginInfo pluginInfo;
    private List<TabInfo> tabInformation;
    private List<InvalidateMethod> invalidMethods;
    private MethodAnnotations methodAnnotations;
    private Map<ExtensionMethod.ParameterType, ExtensionMethods> methods;
    private Collection<Method> conditionalMethods;
    private Collection<Tab> tabAnnotations;

    private static final String WAS_OVER_50_CHARACTERS = "' was over 50 characters.";

    public ExtensionExtractor(DataExtension extension) {
        this.extension = extension;
        extensionName = extension.getClass().getSimpleName();
    }

    /**
     * @deprecated Use {@link DataExtension#getPluginName()} instead.
     */
    @Deprecated
    public static <T extends DataExtension> String getPluginName(Class<T> extensionClass) {
        return getClassAnnotation(extensionClass, PluginInfo.class).map(PluginInfo::name)
                .orElseThrow(() -> new IllegalArgumentException("Given class had no PluginInfo annotation"));
    }

    private static <V extends DataExtension, T extends Annotation> Optional<T> getClassAnnotation(Class<V> from, Class<T> ofClass) {
        return Optional.ofNullable(from.getAnnotation(ofClass));
    }

    /**
     * Use this method in an unit test to validate your DataExtension.
     *
     * @throws IllegalArgumentException If an implementation error is found.
     */
    public void validateAnnotations() {
        extractPluginInfo();
        extractInvalidMethods();
        extractMethods();
        extractTabInfo();

        if (!warnings.isEmpty()) {
            throw new IllegalArgumentException("Warnings: " + warnings.toString());
        }
    }

    private Collection<ExtensionMethod> getExtensionMethods() {
        List<ExtensionMethod> extensionMethods = new ArrayList<>();
        for (Method method : extension.getClass().getMethods()) {
            try {
                extensionMethods.add(new ExtensionMethod(extension, method));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(extensionName + '.' + e.getMessage());
            }
        }
        return extensionMethods;
    }

    /**
     * @deprecated No longer used anywhere, no-op.
     */
    @Deprecated
    public void extractAnnotationInformation() {
        // no-op
    }

    private void extractMethods() {
        methodAnnotations = new MethodAnnotations();
        methods = new EnumMap<>(ExtensionMethod.ParameterType.class);
        methods.put(ExtensionMethod.ParameterType.SERVER_NONE, new ExtensionMethods());
        methods.put(ExtensionMethod.ParameterType.PLAYER_STRING, new ExtensionMethods());
        methods.put(ExtensionMethod.ParameterType.PLAYER_UUID, new ExtensionMethods());
        methods.put(ExtensionMethod.ParameterType.GROUP, new ExtensionMethods());

        conditionalMethods = new ArrayList<>();
        tabAnnotations = new ArrayList<>();

        for (ExtensionMethod method : getExtensionMethods()) {
            if (method.isInaccessible()) {
                continue;
            }

            try {
                method.makeAccessible();
            } catch (SecurityException failedToMakeAccessible) {
                throw new IllegalArgumentException(extensionName + "." + method.getMethodName() + " could not be made accessible: " +
                        failedToMakeAccessible.getMessage(), failedToMakeAccessible);
            }

            method.getAnnotation(BooleanProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addBooleanMethod(method);
                methodAnnotations.put(method.getMethod(), BooleanProvider.class, annotation);
            });
            method.getAnnotation(NumberProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addNumberMethod(method);
                methodAnnotations.put(method.getMethod(), NumberProvider.class, annotation);
            });
            method.getAnnotation(DoubleProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addDoubleMethod(method);
                methodAnnotations.put(method.getMethod(), DoubleProvider.class, annotation);
            });
            method.getAnnotation(PercentageProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addPercentageMethod(method);
                methodAnnotations.put(method.getMethod(), PercentageProvider.class, annotation);
            });
            method.getAnnotation(StringProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addStringMethod(method);
                methodAnnotations.put(method.getMethod(), StringProvider.class, annotation);
            });
            method.getAnnotation(TableProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addTableMethod(method);
                methodAnnotations.put(method.getMethod(), TableProvider.class, annotation);
            });
            method.getAnnotation(GroupProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addGroupMethod(method);
                methodAnnotations.put(method.getMethod(), GroupProvider.class, annotation);
            });
            method.getAnnotation(DataBuilderProvider.class).ifPresent(annotation -> {
                validateMethod(method, annotation);
                methods.get(method.getParameterType()).addDataBuilderMethod(method);
                methodAnnotations.put(method.getMethod(), DataBuilderProvider.class, annotation);
            });

            method.getAnnotation(Conditional.class).ifPresent(annotation -> {
                conditionalMethods.add(method.getMethod());
                methodAnnotations.put(method.getMethod(), Conditional.class, annotation);
            });
            method.getAnnotation(Tab.class).ifPresent(annotation -> {
                tabAnnotations.add(annotation);
                methodAnnotations.put(method.getMethod(), Tab.class, annotation);
            });
        }

        if (methodAnnotations.isEmpty()) {
            throw new IllegalArgumentException(extensionName + " class had no methods annotated with a Provider annotation");
        }

        validateConditionals();
    }

    private <T> void validateReturnType(Method method, Class<T> expectedType) {
        Class<?> returnType = method.getReturnType();
        if (!expectedType.isAssignableFrom(returnType)) {
            String expectedName = expectedType.getName();
            throw new IllegalArgumentException(extensionName + "." + method.getName() +
                    " has invalid return type. was: " +
                    returnType.getName() +
                    ", expected: " +
                    (expectedName.startsWith("[L") ? expectedName + " (an array)" : expectedName));
        }
    }

    private void validateMethodAnnotationPropertyLength(String property, String name, int maxLength, Method method) {
        if (property.length() > maxLength) {
            warnings.add(extensionName + "." + method.getName() + " '" + name + "' was over " + maxLength + " characters.");
        }
    }

    private void validateMethodArguments(Method method, boolean parameterIsRequired, Class... parameterOptions) {
        Class<?>[] parameterTypes = method.getParameterTypes();

        // Possible parameters for the methods:
        // UUID playerUUID, String playerName, Group group, none

        int parameters = parameterTypes.length;

        if (parameterIsRequired && parameters == 0) {
            // Does not have parameters, but one is required
            throw new IllegalArgumentException(extensionName + "." + method.getName() + " requires one of " + Arrays.toString(parameterOptions) + " as a parameter.");
        } else if (parameters == 0) {
            // Has no parameters & it is acceptable.
            return;
        }

        if (parameters > 1) {
            // Has too many parameters
            throw new IllegalArgumentException(extensionName + "." + method.getName() + " has too many parameters, only one of " + Arrays.toString(parameterOptions) + " is required as a parameter.");
        }

        Class<?> methodParameter = parameterTypes[0];

        boolean validParameter = false;
        for (Class<?> option : parameterOptions) {
            if (option.equals(methodParameter)) {
                validParameter = true;
                break;
            }
        }

        if (!validParameter) {
            // Has invalid parameter
            throw new IllegalArgumentException(extensionName + "." + method.getName() + " has invalid parameter: '" + methodParameter.getName() + "' one of " + Arrays.toString(parameterOptions) + " is required as a parameter.");
        }
        // Has valid parameter & it is acceptable.
    }

    private void validateMethod(ExtensionMethod extensionMethod, BooleanProvider annotation) {
        Method method = extensionMethod.getMethod();
        validateReturnType(method, boolean.class);
        validateMethodAnnotationPropertyLength(annotation.text(), "text", 50, method);
        validateMethodAnnotationPropertyLength(annotation.description(), "description", 150, method);
        validateMethodAnnotationPropertyLength(annotation.conditionName(), "conditionName", 50, method);
        validateMethodArguments(method, false, UUID.class, String.class, Group.class);

        String condition = extensionMethod.getAnnotation(Conditional.class).map(Conditional::value).orElse(null);
        if (annotation.conditionName().equals(condition)) {
            warnings.add(extensionName + "." + method.getName() + " can not be conditional of itself. required condition: " + condition + ", provided condition: " + annotation.conditionName());
        }

        if (annotation.conditionName().isEmpty() && annotation.hidden()) {
            throw new IllegalArgumentException(extensionName + "." + method.getName() + " can not be 'hidden' without a 'conditionName'");
        }
    }

    private void validateMethod(ExtensionMethod extensionMethod, NumberProvider annotation) {
        Method method = extensionMethod.getMethod();

        validateReturnType(method, long.class);
        validateMethodAnnotationPropertyLength(annotation.text(), "text", 50, method);
        validateMethodAnnotationPropertyLength(annotation.description(), "description", 150, method);
        validateMethodArguments(method, false, UUID.class, String.class, Group.class);
    }

    private void validateMethod(ExtensionMethod extensionMethod, DoubleProvider annotation) {
        Method method = extensionMethod.getMethod();

        validateReturnType(method, double.class);
        validateMethodAnnotationPropertyLength(annotation.text(), "text", 50, method);
        validateMethodAnnotationPropertyLength(annotation.description(), "description", 150, method);
        validateMethodArguments(method, false, UUID.class, String.class, Group.class);
    }

    private void validateMethod(ExtensionMethod extensionMethod, PercentageProvider annotation) {
        Method method = extensionMethod.getMethod();

        validateReturnType(method, double.class);
        validateMethodAnnotationPropertyLength(annotation.text(), "text", 50, method);
        validateMethodAnnotationPropertyLength(annotation.description(), "description", 150, method);
        validateMethodArguments(method, false, UUID.class, String.class, Group.class);
    }

    private void validateMethod(ExtensionMethod extensionMethod, StringProvider annotation) {
        Method method = extensionMethod.getMethod();

        validateReturnType(method, String.class);
        validateMethodAnnotationPropertyLength(annotation.text(), "text", 50, method);
        validateMethodAnnotationPropertyLength(annotation.description(), "description", 150, method);
        validateMethodArguments(method, false, UUID.class, String.class, Group.class);
    }

    private void validateMethod(ExtensionMethod extensionMethod, TableProvider annotation) {
        Method method = extensionMethod.getMethod();

        validateReturnType(method, Table.class);
        validateMethodArguments(method, false, UUID.class, String.class, Group.class);
    }

    private void validateMethod(ExtensionMethod extensionMethod, GroupProvider annotation) {
        Method method = extensionMethod.getMethod();

        validateReturnType(method, String[].class);
        validateMethodAnnotationPropertyLength(annotation.text(), "text", 50, method);
        validateMethodArguments(method, true, UUID.class, String.class);
    }

    private void validateMethod(ExtensionMethod extensionMethod, DataBuilderProvider annotation) {
        Method method = extensionMethod.getMethod();

        validateReturnType(method, ExtensionDataBuilder.class);
        validateMethodArguments(method, false, UUID.class, String.class, Group.class);
    }

    private void validateConditionals() {
        // Make sure that all methods annotated with Conditional have a Provider annotation
        for (Method conditionalMethod : conditionalMethods) {
            if (!hasAnyOf(conditionalMethod,
                    BooleanProvider.class, DoubleProvider.class, NumberProvider.class,
                    PercentageProvider.class, StringProvider.class, TableProvider.class,
                    GroupProvider.class, DataBuilderProvider.class
            )) {
                throw new IllegalArgumentException(extensionName + "." + conditionalMethod.getName() + " did not have any associated Provider for Conditional.");
            }
            if (hasAnyOf(conditionalMethod, DataBuilderProvider.class)) {
                throw new IllegalArgumentException(extensionName + "." + conditionalMethod.getName() + " had Conditional, but DataBuilderProvider does not support it!");
            }
        }
    }

    private boolean hasAnyOf(Method method, Class<?>... annotationClasses) {
        for (Annotation annotation : method.getAnnotations()) {
            for (Class<?> annotationClass : annotationClasses) {
                if (annotationClass.isAssignableFrom(annotation.getClass())) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T extends Annotation> Optional<T> getClassAnnotation(Class<T> ofClass) {
        return getClassAnnotation(extension.getClass(), ofClass);
    }

    private void extractPluginInfo() {
        pluginInfo = getClassAnnotation(PluginInfo.class)
                .orElseThrow(() -> new IllegalArgumentException("Given class had no PluginInfo annotation"));

        if (pluginInfo.name().length() > 50) {
            warnings.add(extensionName + " PluginInfo 'name" + WAS_OVER_50_CHARACTERS);
        }
    }

    private void extractTabInfo() {
        tabInformation = new ArrayList<>();
        getClassAnnotation(TabInfo.Multiple.class).ifPresent(tabs -> {
            for (TabInfo tabInfo : tabs.value()) {
                String tabName = tabInfo.tab();

                // Length restriction check
                if (tabName.length() > 50) {
                    warnings.add(extensionName + " tabName '" + tabName + WAS_OVER_50_CHARACTERS);
                }

                tabInformation.add(tabInfo);
            }
        });

        getClassAnnotation(TabOrder.class).map(TabOrder::value)
                .ifPresent(order -> {
                    List<String> orderAsList = Arrays.asList(order);
                    // Order by the 2nd list. https://stackoverflow.com/a/18130019
                    // O(n^2 log n), not very bad here because there aren't going to be more than 10 tabs maybe ever.
                    tabInformation.sort(Comparator.comparingInt(item -> orderAsList.indexOf(item.tab())));
                });

        Set<String> tabNames = getTabAnnotations().stream().map(Tab::value).collect(Collectors.toSet());

        // Check for unused TabInfo annotations
        for (TabInfo tabInfo : tabInformation) {
            String tabName = tabInfo.tab();

            if (tabName.length() > 50) {
                warnings.add(extensionName + " TabInfo " + tabName + " 'name" + WAS_OVER_50_CHARACTERS);
            }
        }

        // Check Tab name lengths
        for (String tabName : tabNames) {
            if (tabName.length() > 50) {
                warnings.add(extensionName + " Tab '" + tabName + "' 'name" + WAS_OVER_50_CHARACTERS);
            }
        }
    }

    private void extractInvalidMethods() {
        invalidMethods = new ArrayList<>();
        getClassAnnotation(InvalidateMethod.Multiple.class).ifPresent(tabs -> {
            for (InvalidateMethod tabInfo : tabs.value()) {
                String methodName = tabInfo.value();

                // Length restriction check
                if (methodName.length() > 50) {
                    warnings.add(extensionName + " invalidated method '" + methodName + WAS_OVER_50_CHARACTERS);
                }

                invalidMethods.add(tabInfo);
            }
        });
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public PluginInfo getPluginInfo() {
        if (pluginInfo == null) extractPluginInfo();
        return pluginInfo;
    }

    public Optional<TabOrder> getTabOrder() {
        return getClassAnnotation(TabOrder.class);
    }

    public Collection<Tab> getTabAnnotations() {
        if (tabAnnotations == null) extractMethods();
        return tabAnnotations;
    }

    public List<TabInfo> getTabInformation() {
        if (tabInformation == null) extractTabInfo();
        return tabInformation;
    }

    @Deprecated
    public MethodAnnotations getMethodAnnotations() {
        if (methodAnnotations == null) extractMethods();
        return methodAnnotations;
    }

    public Map<ExtensionMethod.ParameterType, ExtensionMethods> getMethods() {
        if (methods == null) extractMethods();
        return methods;
    }

    public List<InvalidateMethod> getInvalidateMethodAnnotations() {
        if (invalidMethods == null) extractInvalidMethods();
        return invalidMethods;
    }
}
