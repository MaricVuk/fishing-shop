package rs.fishingshop.invoices;

import org.springframework.stereotype.Component;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobQueue;
import rs.fishingshop.jobs.JobType;

@Component
class OrderCreditNoteHandler implements JobHandler {

    private final CreditNoteService creditNotes;
    private final JobQueue jobs;

    OrderCreditNoteHandler(CreditNoteService creditNotes, JobQueue jobs) {
        this.creditNotes = creditNotes;
        this.jobs = jobs;
    }

    @Override
    public JobType type() {
        return JobType.ORDER_CREDIT_NOTE;
    }

    @Override
    public void handle(ClaimedJob job) {
        if (creditNotes.findForOrder(job.orderId()).isEmpty()) {
            creditNotes.issue(job.orderId());
        }
        jobs.enqueueForOrderUnlessQueued(JobType.ORDER_CREDIT_NOTE_EMAIL, job.orderId());
    }
}
