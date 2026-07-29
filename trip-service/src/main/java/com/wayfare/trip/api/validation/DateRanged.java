package com.wayfare.trip.api.validation;

import java.time.LocalDate;

/** Implemented by any request DTO carrying a start/end date pair (record accessors match). */
public interface DateRanged {
    LocalDate startDate();
    LocalDate endDate();
}
