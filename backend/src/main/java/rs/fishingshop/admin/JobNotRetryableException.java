package rs.fishingshop.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import rs.fishingshop.jobs.JobStatus;

class JobNotRetryableException extends ErrorResponseException {

    JobNotRetryableException(long id, JobStatus status) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Job " + id + " is " + status + ", only a DEAD job can be retried"), null);
    }
}
