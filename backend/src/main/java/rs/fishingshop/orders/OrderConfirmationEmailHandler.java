package rs.fishingshop.orders;

import org.springframework.stereotype.Component;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.mail.ShopMail;

@Component
class OrderConfirmationEmailHandler implements JobHandler {

    private final CustomerOrderRepository orders;
    private final ShopMail mail;

    OrderConfirmationEmailHandler(CustomerOrderRepository orders, ShopMail mail) {
        this.orders = orders;
        this.mail = mail;
    }

    @Override
    public JobType type() {
        return JobType.ORDER_CONFIRMATION_EMAIL;
    }

    @Override
    public void handle(ClaimedJob job) {
        CustomerOrder order = orders.findWithItems(job.orderId())
                .orElseThrow(() -> new IllegalStateException("Order " + job.orderId() + " does not exist"));
        mail.send(order.getCustomerEmail(), "order-confirmation-" + order.getPublicToken(),
                "Potvrda porudžbine " + order.getPublicNumber(), body(order), null);
    }

    private String body(CustomerOrder order) {
        StringBuilder text = new StringBuilder()
                .append("Zdravo ").append(order.getCustomerName()).append(",\n\n")
                .append("hvala na porudžbini. Primili smo je i javićemo se kada bude poslata.\n\n")
                .append("Porudžbina ").append(order.getPublicNumber()).append("\n\n");
        for (OrderItem item : order.getItems()) {
            text.append(item.getProductName()).append(" (").append(item.getSku()).append(")\n")
                    .append("  ").append(item.getQuantity()).append(" x ")
                    .append(RsdFormat.withCurrency(item.getUnitPrice()))
                    .append(" = ").append(RsdFormat.withCurrency(item.lineTotal())).append("\n");
        }
        text.append("\nUkupno: ").append(RsdFormat.withCurrency(order.getTotal())).append("\n\n")
                .append("Adresa za dostavu:\n").append(order.getShippingAddress()).append("\n\n")
                .append(mail.signature()).append("\n");
        return text.toString();
    }
}
