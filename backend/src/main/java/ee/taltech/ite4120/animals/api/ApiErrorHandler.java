package ee.taltech.ite4120.animals.api;

import ee.taltech.ite4120.animals.ownerregistry.OwnerRegistryException;
import ee.taltech.ite4120.reports.addressregister.AddressRegisterException;
import ee.taltech.ite4120.reports.service.LimitExceededException;
import ee.taltech.ite4120.reports.smartid.SmartIdException;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ApiException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.ForbiddenException;
import org.helex.commons.exception.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * One error shape for every endpoint: RFC 9457 problem details
 * ({@code application/problem+json}). 4xx = the caller's fault, 5xx = ours;
 * never a stack trace, never framework HTML. This is the S3 error-model slide
 * as running code.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail beanValidation(MethodArgumentNotValidException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setDetail(e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("request body is invalid"));
        return pd;
    }

    @ExceptionHandler(ApiClientException.class)
    public ProblemDetail clientError(ApiClientException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request");
        pd.setDetail(detail(e));
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadableBody(HttpMessageNotReadableException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request");
        pd.setDetail("request body cannot be read");
        return pd;
    }

    /** A request parameter that is missing or of the wrong type. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ProblemDetail badParameter(Exception e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request");
        String name = e instanceof MethodArgumentTypeMismatchException m ? m.getName()
                : ((MissingServletRequestParameterException) e).getParameterName();
        pd.setDetail(name + " is missing or not valid");
        return pd;
    }

    @ExceptionHandler(LimitExceededException.class)
    public ResponseEntity<ProblemDetail> overLimit(LimitExceededException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        pd.setTitle("Too many reports");
        pd.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds())).body(pd);
    }

    @ExceptionHandler({SmartIdException.class, AddressRegisterException.class})
    public ProblemDetail upstreamFailure(RuntimeException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        pd.setTitle("Upstream service unavailable");
        pd.setDetail(e instanceof SmartIdException ? e.getMessage() : "address register is not available");
        return pd;
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail forbidden(ForbiddenException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Forbidden");
        pd.setDetail(detail(e));
        return pd;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not found");
        pd.setDetail(detail(e));
        return pd;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Conflict with current state");
        pd.setDetail(detail(e));
        return pd;
    }

    /**
     * The commons exceptions render their message as {@code "CODE: text"} — the code
     * is for logs and machine matching, the text is for people. The problem document
     * carries the human text; the status code already says what kind of error it is.
     */
    private static String detail(ApiException e) {
        String message = e.getMessage();
        return message == null ? null : message.replaceFirst("^[\\w-]+: ", "");
    }

    /** The registry being down is an upstream failure, not the caller's fault: 502. */
    @ExceptionHandler(OwnerRegistryException.class)
    public ProblemDetail registryFailure(OwnerRegistryException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        pd.setTitle("Owner registry unavailable");
        pd.setDetail(e.getMessage());
        return pd;
    }
}
