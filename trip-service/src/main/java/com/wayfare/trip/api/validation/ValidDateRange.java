package com.wayfare.trip.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Design §6.4 example: a custom Jakarta Bean Validation constraint, not a hand-rolled service check. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDateRangeValidator.class)
public @interface ValidDateRange {
    String message() default "endDate must not be before startDate";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
