package rs.fishingshop.reports;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

record ReportPeriod(LocalDate from, LocalDate to) {

    static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Belgrade");
    private static final int DEFAULT_DAYS = 30;

    ReportPeriod {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
    }

    static ReportPeriod resolve(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(SHOP_ZONE);
        return new ReportPeriod(from != null ? from : end.minusDays(DEFAULT_DAYS - 1), end);
    }
}
