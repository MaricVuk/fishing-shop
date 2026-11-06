package rs.fishingshop.invoices;

import org.springframework.stereotype.Component;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.mail.MailAttachment;
import rs.fishingshop.mail.ShopMail;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.CustomerOrderRepository;
import rs.fishingshop.orders.OrderStatus;

@Component
class OrderInvoiceHandler implements JobHandler {

    private final CustomerOrderRepository orders;
    private final InvoiceService invoices;
    private final ShopMail mail;

    OrderInvoiceHandler(CustomerOrderRepository orders, InvoiceService invoices, ShopMail mail) {
        this.orders = orders;
        this.invoices = invoices;
        this.mail = mail;
    }

    @Override
    public JobType type() {
        return JobType.ORDER_INVOICE;
    }

    @Override
    public void handle(ClaimedJob job) {
        CustomerOrder order = orders.findWithItems(job.orderId())
                .orElseThrow(() -> new IllegalStateException("Order " + job.orderId() + " does not exist"));
        StoredInvoice existing = invoices.findForOrder(order.getId()).orElse(null);
        if (existing == null && order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        StoredInvoice invoice = existing != null ? existing : invoices.issue(order.getId());
        String text = "Zdravo " + order.getCustomerName() + ",\n\n"
                + "u prilogu je račun " + invoice.number() + " za porudžbinu #" + order.getId() + ".\n\n"
                + mail.signature() + "\n";
        mail.send(order.getCustomerEmail(), "order-invoice-" + order.getId(), "Račun " + invoice.number(), text,
                new MailAttachment(invoice.number() + ".pdf", invoice.pdf(), "application/pdf"));
    }
}
