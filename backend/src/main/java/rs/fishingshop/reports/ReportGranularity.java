package rs.fishingshop.reports;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public enum ReportGranularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlUnit;

    ReportGranularity(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    String sqlUnit() {
        return sqlUnit;
    }

    LocalDate bucketStart(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    long bucketCount(LocalDate from, LocalDate to) {
        LocalDate first = bucketStart(from);
        LocalDate last = bucketStart(to);
        return switch (this) {
            case DAY -> ChronoUnit.DAYS.between(first, last) + 1;
            case WEEK -> ChronoUnit.WEEKS.between(first, last) + 1;
            case MONTH -> ChronoUnit.MONTHS.between(first, last) + 1;
        };
    }
}
