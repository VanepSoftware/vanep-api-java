package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Constraint;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;

class DtoValidationContractTest {

  @Test
  void allBeanValidationConstraintsUseKnownMessageKeys() throws Exception {
    Set<String> messageKeys = readBundleKeys("messages.properties");
    Set<String> violations = new LinkedHashSet<>();

    for (Class<?> candidate : scanMainClasses()) {
      inspectClass(candidate, messageKeys, violations);
    }

    assertThat(violations)
        .as(
            "Bean Validation annotations must declare message = \"{key}\" existing in messages.properties")
        .isEmpty();
  }

  private static void inspectClass(
      Class<?> candidate, Set<String> messageKeys, Set<String> violations) {
    if (candidate.isAnnotation() || candidate.isInterface() || candidate.isEnum()) {
      return;
    }
    if (candidate.isRecord()) {
      for (RecordComponent component : candidate.getRecordComponents()) {
        checkAnnotations(
            candidate.getName() + "#" + component.getName(),
            component.getAnnotations(),
            messageKeys,
            violations);
        checkAnnotatedType(
            candidate.getName() + "#" + component.getName(),
            component.getAnnotatedType(),
            messageKeys,
            violations);
      }
      return;
    }
    for (Field field : candidate.getDeclaredFields()) {
      if (field.isSynthetic()) {
        continue;
      }
      checkAnnotations(
          candidate.getName() + "#" + field.getName(),
          field.getAnnotations(),
          messageKeys,
          violations);
      checkAnnotatedType(
          candidate.getName() + "#" + field.getName(),
          field.getAnnotatedType(),
          messageKeys,
          violations);
    }
  }

  private static void checkAnnotatedType(
      String location,
      AnnotatedType annotatedType,
      Set<String> messageKeys,
      Set<String> violations) {
    if (annotatedType == null) {
      return;
    }
    checkAnnotations(location, annotatedType.getAnnotations(), messageKeys, violations);
    if (annotatedType instanceof AnnotatedParameterizedType parameterized) {
      for (AnnotatedType arg : parameterized.getAnnotatedActualTypeArguments()) {
        checkAnnotatedType(location + "<arg>", arg, messageKeys, violations);
      }
    } else if (annotatedType instanceof AnnotatedArrayType arrayType) {
      checkAnnotatedType(
          location + "[]", arrayType.getAnnotatedGenericComponentType(), messageKeys, violations);
    } else if (annotatedType instanceof AnnotatedWildcardType wildcard) {
      for (AnnotatedType bound : wildcard.getAnnotatedUpperBounds()) {
        checkAnnotatedType(location + "?extends", bound, messageKeys, violations);
      }
      for (AnnotatedType bound : wildcard.getAnnotatedLowerBounds()) {
        checkAnnotatedType(location + "?super", bound, messageKeys, violations);
      }
    }
  }

  private static void checkAnnotations(
      String location, Annotation[] annotations, Set<String> messageKeys, Set<String> violations) {
    for (Annotation annotation : annotations) {
      Class<? extends Annotation> type = annotation.annotationType();
      if (!type.isAnnotationPresent(Constraint.class)) {
        continue;
      }
      String template = readMessageTemplate(annotation);
      if (template == null || !template.startsWith("{") || !template.endsWith("}")) {
        violations.add(
            location + " @" + type.getSimpleName() + " has non-key message: " + template);
        continue;
      }
      String key = template.substring(1, template.length() - 1);
      if (key.startsWith("jakarta.validation.") || key.startsWith("org.hibernate.validator.")) {
        violations.add(
            location + " @" + type.getSimpleName() + " relies on default Jakarta key: " + template);
        continue;
      }
      if (!messageKeys.contains(key)) {
        violations.add(
            location + " @" + type.getSimpleName() + " references missing message key: " + key);
      }
    }
  }

  private static String readMessageTemplate(Annotation annotation) {
    try {
      Object value = annotation.annotationType().getMethod("message").invoke(annotation);
      return value instanceof String text ? text : null;
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static List<Class<?>> scanMainClasses() throws IOException {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
    Resource[] resources = resolver.getResources("classpath*:br/com/vanep/**/*.class");
    List<Class<?>> classes = new ArrayList<>();
    ClassLoader classLoader = DtoValidationContractTest.class.getClassLoader();
    for (Resource resource : resources) {
      String uri = resource.getURI().toString();
      if (uri.contains("/test-classes/")) {
        continue;
      }
      String className =
          readerFactory.getMetadataReader(resource).getClassMetadata().getClassName();
      if (className.endsWith("Test")) {
        continue;
      }
      try {
        classes.add(Class.forName(className, false, classLoader));
      } catch (ClassNotFoundException | LinkageError ignored) {
        // Ignore uninitialized inner helpers outside the runtime graph
      }
    }
    return classes;
  }

  private static Set<String> readBundleKeys(String path) throws IOException {
    String content = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    Set<String> keys = new LinkedHashSet<>();
    for (String rawLine : content.split("\\R")) {
      String line = rawLine.strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq > 0) {
        keys.add(line.substring(0, eq).strip());
      }
    }
    return keys;
  }
}
