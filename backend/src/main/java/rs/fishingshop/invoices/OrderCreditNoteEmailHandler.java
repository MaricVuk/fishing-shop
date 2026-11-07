package rs.fishingshop.invoices;

import org.springframework.stereotype.Component;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.mail.MailAttachment;
import rs.fishingshop.mail.ShopMail;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.CustomerOrderRepository;

@Component
class OrderCreditNoteEmailHandler implements JobHandler {

    private final CustomerOrderRepository orders;
    private final CreditNoteService creditNotes;
    private final ShopMail mail;

    OrderCreditNoteEmailHandler(CustomerOrderRepository orders, CreditNoteService creditNotes, ShopMail mail) {
        this.orders = orders;
        this.creditNotes = creditNotes;
        this.mail = mail;
    }

    @Override
    public JobType type() {
        return JobType.ORDER_CREDIT_NOTE_EMAIL;
    }

    @Override
    public void handle(ClaimedJob job) {
        CustomerOrder order = orders.findById(job.orderId())
                .orElseThrow(() -> new IllegalStateException("Order " + job.orderId() + " does not exist"));
        StoredInvoice creditNote = creditNotes.findForOrder(order.getId())
                .orElseThrow(() -> new IllegalStateException("Order " + order.getId() + " has no credit note"));
        String invoice = creditNotes.reversedInvoiceNumber(order.getId()).orElseThrow();
        String text = "Zdravo " + order.getCustomerName() + ",\n\n"
                + "porudžbina " + order.getPublicNumber() + " je otkazana. U prilogu je storno " + creditNote.number()
                + " kojim se poništava račun " + invoice + ".\n\n"
                + mail.signature() + "\n";
        mail.send(order.getCustomerEmail(), "order-credit-note-" + order.getPublicToken(),
                "Storno računa " + invoice, text,
                new MailAttachment(creditNote.number() + ".pdf", creditNote.pdf(), "application/pdf"));
    }
}
