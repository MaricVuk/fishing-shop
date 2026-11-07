package rs.fishingshop.invoices;

import java.time.Instant;

public record InvoiceSummary(InvoiceStatus status, String number, Instant issuedAt) {
}
