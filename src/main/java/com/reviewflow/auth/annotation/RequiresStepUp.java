package com.reviewflow.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RequiresStepUp {

  /** Maximum age of {@code stepUpAt} JWT claim in seconds. */
  int maxAgeSeconds() default 300;
}
