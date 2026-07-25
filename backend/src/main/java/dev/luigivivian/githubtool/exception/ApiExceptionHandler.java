package dev.luigivivian.githubtool.exception;

import dev.luigivivian.githubtool.exception.GithubRateLimitedException;
import dev.luigivivian.githubtool.exception.GithubUnavailableException;
import dev.luigivivian.githubtool.exception.GithubUserNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(GithubUserNotFoundException.class)
    public ProblemDetail handleUserNotFound(GithubUserNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "No GitHub user named \"" + e.getUsername() + "\". Check the spelling and try again.");
        problem.setTitle("User not found");
        return problem;
    }

    @ExceptionHandler(GithubResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(GithubResourceNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "\"" + e.getResource() + "\" wasn't found on GitHub. It may have been renamed or removed.");
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(GithubRateLimitedException.class)
    public ProblemDetail handleRateLimited(GithubRateLimitedException e) {
        String retryHint = e.getRetryAfterSeconds() != null
                ? " Try again in about " + humanDuration(e.getRetryAfterSeconds()) + "."
                : " Try again later.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "GitHub's request limit was reached." + retryHint
                        + " Configuring a GITHUB_TOKEN raises the limit.");
        problem.setTitle("GitHub rate limit reached");
        problem.setProperty("retryAfterSeconds", e.getRetryAfterSeconds());
        return problem;
    }

    @ExceptionHandler(GithubAuthExpiredException.class)
    public ProblemDetail handleAuthExpired(GithubAuthExpiredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Your GitHub sign-in expired or was revoked. Sign out and sign in again.");
        problem.setTitle("Sign-in expired");
        return problem;
    }

    @ExceptionHandler(GithubUnavailableException.class)
    public ProblemDetail handleUnavailable(GithubUnavailableException e) {
        log.warn("GitHub request failed", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "GitHub could not be reached right now. Try again in a moment.");
        problem.setTitle("GitHub unavailable");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        return invalidUsernameProblem();
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.badRequest().body(invalidUsernameProblem());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled error", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again.");
        problem.setTitle("Unexpected error");
        return problem;
    }

    private static ProblemDetail invalidUsernameProblem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "That doesn't look like a valid GitHub username. Use letters, numbers and single"
                        + " hyphens (max 39 characters).");
        problem.setTitle("Invalid username");
        return problem;
    }

    private static String humanDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        }
        long minutes = Math.ceilDiv(seconds, 60);
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
