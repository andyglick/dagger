/*
 * Copyright (C) 2014 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** Formats the signature of an {@link ExecutableElement} suitable for use in error messages. */
public final class MethodSignatureFormatter extends Formatter<ExecutableElement> {
  private final DaggerTypes types;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  public MethodSignatureFormatter(DaggerTypes types, InjectionAnnotations injectionAnnotations) {
    this.types = types;
    this.injectionAnnotations = injectionAnnotations;
  }

  /**
   * A formatter that uses the type where the method is declared for the annotations and name of the
   * method, but the method's resolved type as a member of {@code type} for the key.
   */
  public Formatter<XMethodElement> typedFormatter(XType type) {
    checkArgument(isDeclared(type));
    return new Formatter<XMethodElement>() {
      @Override
      public String format(XMethodElement method) {
        return formatMethod(MoreTypes.asDeclared(toJavac(type)), toJavac(method));
      }
    };
  }

  /**
   * A formatter that uses the type where the method is declared for the annotations and name of the
   * method, but the method's resolved type as a member of {@code declaredType} for the key.
   */
  public Formatter<ExecutableElement> typedFormatter(DeclaredType declaredType) {
    return new Formatter<ExecutableElement>() {
      @Override
      public String format(ExecutableElement method) {
        return formatMethod(declaredType, method);
      }
    };
  }

  private String formatMethod(DeclaredType declaredType, ExecutableElement method) {
    return format(
        method,
        MoreTypes.asExecutable(types.asMemberOf(declaredType, method)),
        MoreElements.asType(method.getEnclosingElement()));
  }

  public String format(XMethodElement method) {
    return format(toJavac(method));
  }

  @Override
  public String format(ExecutableElement method) {
    return format(method, Optional.empty());
  }

  /**
   * Formats an ExecutableElement as if it were contained within the container, if the container is
   * present.
   */
  public String format(XExecutableElement method, Optional<XType> container) {
    return format(toJavac(method), container.map(XConverters::toJavac).map(MoreTypes::asDeclared));
  }

  /**
   * Formats an ExecutableElement as if it were contained within the container, if the container is
   * present.
   */
  public String format(ExecutableElement method, Optional<DeclaredType> container) {
    TypeElement type = MoreElements.asType(method.getEnclosingElement());
    ExecutableType executableType = MoreTypes.asExecutable(method.asType());
    if (container.isPresent()) {
      executableType = MoreTypes.asExecutable(types.asMemberOf(container.get(), method));
      type = MoreElements.asType(container.get().asElement());
    }
    return format(method, executableType, type);
  }

  private String format(
      ExecutableElement method, ExecutableType methodType, TypeElement declaringType) {
    StringBuilder builder = new StringBuilder();
    // TODO(user): AnnotationMirror formatter.
    List<? extends AnnotationMirror> annotations = method.getAnnotationMirrors();
    if (!annotations.isEmpty()) {
      Iterator<? extends AnnotationMirror> annotationIterator = annotations.iterator();
      for (int i = 0; annotationIterator.hasNext(); i++) {
        if (i > 0) {
          builder.append(' ');
        }
        builder.append(formatAnnotation(annotationIterator.next()));
      }
      builder.append(' ');
    }
    if (method.getSimpleName().contentEquals("<init>")) {
      builder.append(declaringType.getQualifiedName());
    } else {
      builder
          .append(nameOfType(methodType.getReturnType()))
          .append(' ')
          .append(declaringType.getQualifiedName())
          .append('.')
          .append(method.getSimpleName());
    }
    builder.append('(');
    checkState(method.getParameters().size() == methodType.getParameterTypes().size());
    Iterator<? extends VariableElement> parameters = method.getParameters().iterator();
    Iterator<? extends TypeMirror> parameterTypes = methodType.getParameterTypes().iterator();
    for (int i = 0; parameters.hasNext(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      appendParameter(builder, parameters.next(), parameterTypes.next());
    }
    builder.append(')');
    return builder.toString();
  }

  private void appendParameter(StringBuilder builder, VariableElement parameter, TypeMirror type) {
    injectionAnnotations
        .getQualifier(parameter)
        .ifPresent(
            qualifier -> {
              builder.append(formatAnnotation(qualifier)).append(' ');
            });
    builder.append(nameOfType(type));
  }

  private static String nameOfType(TypeMirror type) {
    return stripCommonTypePrefixes(type.toString());
  }

  private static String formatAnnotation(AnnotationMirror annotation) {
    return stripCommonTypePrefixes(annotation.toString());
  }
}
