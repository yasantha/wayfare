package com.wayfare.trip.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, DateRanged> {

    @Override
    public boolean isValid(DateRanged value, ConstraintValidatorContext context) {
        if (value == null || value.startDate() == null || value.endDate() == null) {
            return true; // let @NotNull on the individual fields handle absence
        }
        return !value.endDate().isBefore(value.startDate());
    }
}
