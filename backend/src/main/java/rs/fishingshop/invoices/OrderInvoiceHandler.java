package rs.fishingshop.invoices;

import org.springframework.stereotype.Component;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobQueue;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.CustomerOrderRepository;
import rs.fishingshop.orders.OrderStatus;

@Component
class OrderInvoiceHandler implements JobHandler {

    private final CustomerOrderRepository orders;
    private final InvoiceService invoices;
    private final JobQueue jobs;

    OrderInvoiceHandler(CustomerOrderRepository orders, InvoiceService invoices, JobQueue jobs) {
        this.orders = orders;
        this.invoices = invoices;
        this.jobs = jobs;
    }

    @Override
    public JobType type() {
        return JobType.ORDER_INVOICE;
    }

    @Override
    public void handle(ClaimedJob job) {
        CustomerOrder order = orders.findWithItems(job.orderId())
                .orElseThrow(() -> new IllegalStateException("Order " + job.orderId() + " does not exist"));
        boolean issued = invoices.findForOrder(order.getId()).isPresent();
        if (!issued && order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (!issued) {
            invoices.issue(order.getId());
        }
        jobs.enqueueForOrderUnlessQueued(JobType.ORDER_INVOICE_EMAIL, order.getId());
    }
}
