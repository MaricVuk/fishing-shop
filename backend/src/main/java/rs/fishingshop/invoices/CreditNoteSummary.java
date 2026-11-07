package rs.fishingshop.invoices;

import java.time.Instant;

public record CreditNoteSummary(InvoiceStatus status, String number, Instant issuedAt) {
}
