// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.util.Comparator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opengroup.osdu.core.common.exception.NotFoundException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.exception.DeleteRecordsException;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.http.HttpStatus.SC_MULTI_STATUS;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class GlobalExceptionMapper extends ResponseEntityExceptionHandler {

    @Autowired
    private JaxRsDpsLog jaxRsDpsLogger;

    @ExceptionHandler(AppException.class)
    protected ResponseEntity<Object> handleAppException(AppException e) {
        return this.getErrorResponse(e);
    }

    @ExceptionHandler(ValidationException.class)
    protected ResponseEntity<Object> handleValidationException(ValidationException e) {
        if (e.getCause() instanceof RequestValidationException) {
            return handleRequestValidationException((RequestValidationException)e.getCause());
        }
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", e.getMessage(), e));
    }

    @ExceptionHandler(RequestValidationException.class)
    protected ResponseEntity<Object> handleRequestValidationException(RequestValidationException e) {
        return new ResponseEntity<>(getValidationResponse(e.getStatus(), e.getReason(), e.getMessage()), e.getStatus());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<Object> handleConstraintValidationException(ConstraintViolationException e) {
        List<String> msgs = new ArrayList<String>();
        for (ConstraintViolation violation : e.getConstraintViolations()) {
            msgs.add(violation.getMessage());
        }
        if (msgs.isEmpty()) {
            msgs.add("Invalid payload");
        }
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode array = mapper.valueToTree(msgs);
        JsonNode result = mapper.createObjectNode().set("errors", array);
        return this.getErrorResponse(new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", result.toString()));
    }

    @ExceptionHandler(NotFoundException.class)
    protected ResponseEntity<Object> handleNotFoundException(NotFoundException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.NOT_FOUND.value(), "Resource not found.", e.getMessage(), e));
    }

    @ExceptionHandler(UnrecognizedPropertyException.class)
    protected ResponseEntity<Object> handleUnrecognizedPropertyException(UnrecognizedPropertyException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Unrecognized property.", e.getMessage(), e));
    }

    @ExceptionHandler(JsonProcessingException.class)
    protected ResponseEntity<Object> handleJsonProcessingException(JsonProcessingException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Failed to process JSON.", e.getMessage(), e));
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.FORBIDDEN.value(), "Access denied", e.getMessage(), e));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleIOException(IOException e) {
        if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "Broken pipe")) {
            this.jaxRsDpsLogger.warning("Client closed the connection while request still being processed");
            return null;
        } else {
            return this.getErrorResponse(
                    new AppException(HttpStatus.SERVICE_UNAVAILABLE.value(), "Unknown error", e.getMessage(), e));
        }
    }

    @ExceptionHandler(DeleteRecordsException.class)
    protected ResponseEntity<Object> handleDeleteRecordsException(DeleteRecordsException e) {
        JsonArray responseArray = new JsonArray();

        e.getNotDeletedRecords().stream()
                .map(pair -> {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.add("notDeletedRecordId", new JsonPrimitive(pair.getKey()));
                    jsonObject.add("message", new JsonPrimitive(pair.getValue()));
                    return jsonObject;
                })
                .forEach(responseArray::add);
        return ResponseEntity.status(SC_MULTI_STATUS).body(responseArray.toString());
    }

    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(@NonNull HttpRequestMethodNotSupportedException e,
                                                                         @NonNull HttpHeaders headers,
                                                                         @NonNull HttpStatusCode status,
                                                                         @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED, "Method not found.",
                        "Method not found.", e));
    }

    /**
     * Unsupported {@code Content-Type} on the request (HTTP 415). Without this override,
     * {@link ResponseEntityExceptionHandler} returns RFC 7807 {@code application/problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            @NonNull org.springframework.web.HttpMediaTypeNotSupportedException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Unsupported media type.",
                        e.getMessage(), e));
    }

    /**
     * No acceptable representation for the request {@code Accept} header (HTTP 406). Without this
     * override, {@link ResponseEntityExceptionHandler} returns RFC 7807 {@code application/problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            @NonNull org.springframework.web.HttpMediaTypeNotAcceptableException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.NOT_ACCEPTABLE.value(), "Not acceptable.",
                        e.getMessage(), e));
    }

    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException e,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error", e.getMessage(), e));
    }

    /**
     * Handles a {@code @PathVariable}/{@code @RequestParam} type conversion failure (e.g. a non-numeric
     * {@code version} path segment on {@code GET /records/{id}/{version}}), reported via
     * {@link MethodArgumentTypeMismatchException}. Without this override, Spring's default handling in
     * {@link ResponseEntityExceptionHandler} returns an RFC 7807 {@code application/problem+json} response,
     * inconsistent with every other error response in this service. Normalizing here keeps all 400 responses on a
     * single, consistent {@code application/json} {@link org.opengroup.osdu.core.common.model.http.AppError} shape.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String errorMessage = String.format("Invalid value '%s' for parameter '%s'", e.getValue(), e.getName());
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error", errorMessage, e));
    }

    /**
     * Defensive override for Spring MVC's built-in method-validation path
     * ({@link HandlerMethodValidationException} → RFC 7807 {@code application/problem+json} by default).
     * <p>
     * Storage controllers use class-level {@code @Validated}, which skips that path: the AOP proxy
     * throws {@link ConstraintViolationException} instead (see {@link #handleConstraintValidationException}).
     * Kept so any controller without {@code @Validated} still returns {@code AppError} rather than
     * {@code problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        List<String> errors = new ArrayList<>();
        e.getAllValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error -> {
                    String message = error.getDefaultMessage();
                    if (StringUtils.isNotBlank(message)) {
                        errors.add(message);
                    }
                }));
        String errorMessage = errors.isEmpty() ? "Validation error." : String.join("; ", errors);
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", errorMessage, e));
    }

    /**
     * Missing required {@code @RequestParam} (e.g. {@code kind} on {@code GET /query/records}).
     * Spring routes this to {@code handleMissingServletRequestParameter}, not
     * {@link #handleServletRequestBindingException}; without this override the default RFC 7807
     * {@code application/problem+json} body is returned.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull org.springframework.web.bind.MissingServletRequestParameterException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", e.getMessage(), e));
    }

    /**
     * Servlet binding failures routed through this hook (e.g. {@code MissingRequestHeaderException} → 400).
     * Uses Spring's mapped {@code status} so server-side cases like {@code MissingPathVariableException}
     * (controller mapping bug, default 500) are not rewritten to 400.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleServletRequestBindingException(
            @NonNull org.springframework.web.bind.ServletRequestBindingException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String reason = status.is4xxClientError() ? "Validation error." : "Server error.";
        return this.getErrorResponse(
                new AppException(status.value(), reason, e.getMessage(), e));
    }

    public ResponseEntity<Object> getErrorResponse(AppException e) {

        String exceptionMsg = e.getOriginalException() != null
                ? e.getOriginalException().getMessage()
                : e.getError().getMessage();

        if (e.getError().getCode() > 499) {
            this.jaxRsDpsLogger.error(exceptionMsg, e);
        } else {
            this.jaxRsDpsLogger.warning(exceptionMsg, e);
        }

        return new ResponseEntity<Object>(e.getError(), HttpStatus.resolve(e.getError().getCode()));
    }

    private ObjectNode getValidationResponse(HttpStatus status, String reason, String message) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("code", status.value());
        node.put("reason", reason);
        node.put("message", message);
        return node;
    }


    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        BindingResult bindingResult = ex.getBindingResult();
        String errorMessage = bindingResult.getAllErrors()
            .stream()
            .sorted(Comparator.comparing(ObjectError::getDefaultMessage, Comparator.nullsFirst(String::compareTo)))
            .toList()
            .get(0).getDefaultMessage();
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.",errorMessage));
    }
}
