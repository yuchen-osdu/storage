// Copyright 2017-2019, Schlumberger
// Copyright © Microsoft Corporation
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
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.exception.NotFoundException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.exception.DeleteRecordsException;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.io.IOException;
import java.util.Collections;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GlobalExceptionMapperTest {

	@InjectMocks
	private GlobalExceptionMapper sut;

	@Mock
	private JaxRsDpsLog logger;

	@Test
	public void should_useValuesInAppExceptionInResponse_when_appExceptionIsHandledByGlobalExceptionMapper() {

		AppException exception = new AppException(409, "any reason", "any message");

		ResponseEntity response = this.sut.handleAppException(exception);
		assertEquals(409, response.getStatusCode().value());

		verify(this.logger).warning("any message", exception);
	}

	@Test
	public void should_returnBadRequest_when_NotSupportedExceptionIsCaptured() {
		ValidationException diException = new ValidationException("my bad");

		ResponseEntity response = this.sut.handleValidationException(diException);
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
	}

	@Test
	public void should_logErrorMessage_when_statusCodeLargerThan499() {
		Exception originalException = new Exception("any message");
		AppException appException = new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Server error.",
				"An unknown error has occurred.", originalException);

		this.sut.handleAppException(appException);

		verify(this.logger).error("any message", appException);
	}

	@Test
	public void should_logWarningMessage_when_statusCodeSmallerThan499() {

		NotFoundException originalException = new NotFoundException("any message");
		AppException appException = new AppException(HttpStatus.SC_NOT_FOUND, "Resource not found.",
				"any message", originalException);

		this.sut.handleNotFoundException(originalException);

		verify(this.logger).warning("any message", appException);

	}

	@Test
	public void should_returnUnprocessableEntity_with_correct_reason_when_JsonProcessingException_Is_Captured() {
		JsonProcessingException exception = Mockito.mock(JsonProcessingException.class);
		ResponseEntity response = this.sut.handleJsonProcessingException(exception);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Failed to process JSON.", ((AppError)response.getBody()).getReason());
	}

	@Test
	public void should_returnUnprocessableEntity_with_correct_reason_when_UnrecognizedPropertyException_Is_Captured() {
		UnrecognizedPropertyException exception = Mockito.mock(UnrecognizedPropertyException.class);
		ResponseEntity response = this.sut.handleUnrecognizedPropertyException(exception);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Unrecognized property.", ((AppError)response.getBody()).getReason());
	}

	@Test
	public void should_returnNullResponse_when_BrokenPipeIOExceptionIsCaptured() {
		IOException ioException = new IOException("Broken pipe");

		ResponseEntity response = this.sut.handleIOException(ioException);

		assertNull(response);
	}

	@Test
	public void should_returnServiceUnavailable_when_IOExceptionIsCaptured() {
		IOException ioException = new IOException("Not broken yet");

		ResponseEntity response = this.sut.handleIOException(ioException);

		assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, response.getStatusCode().value());
	}


	@Test
	public void should_returnSortedErrorMessages_when_MethodArgumentNotValidExceptionCaptured() {
		//given
		HttpHeaders headers = null;
		HttpStatusCode status = null;
		WebRequest request = null;
		BindingResult bindingResult = Mockito.spy(BindingResult.class);
		MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);
		ArgumentCaptor<String> errorMessage = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<AppException> appException = ArgumentCaptor.forClass(AppException.class);
		List<ObjectError> objectErrors = new ArrayList<>();
		objectErrors.add(new ObjectError("", "B_defaultMessage"));
		objectErrors.add(new ObjectError("", "C_defaultMessage"));
		String aDefaultMessage = "A_defaultMessage"; // inserted into a list as not a first parameter
		objectErrors.add(new ObjectError("", aDefaultMessage));
		objectErrors.add(new ObjectError("", "D_defaultMessage"));
		// when
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getAllErrors()).thenReturn(objectErrors);
		// then trigger logic and capture values
		this.sut.handleMethodArgumentNotValid(ex, headers, status, request);
		verify(logger).warning(errorMessage.capture(), appException.capture());
		assertEquals(aDefaultMessage, errorMessage.getValue());
	}

	@Test
	public void should_returnForbidden_when_AccessDeniedExceptionIsCaptured() {
		AccessDeniedException exception = new AccessDeniedException("no access");

		ResponseEntity response = this.sut.handleAccessDeniedException(exception);

		assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Access denied", ((AppError) response.getBody()).getReason());
	}

	@Test
	public void should_returnMultiStatus_when_DeleteRecordsExceptionIsCaptured() {
		List<Pair<String, String>> notDeleted = new ArrayList<>();
		notDeleted.add(Pair.of("record1", "Access denied"));
		notDeleted.add(Pair.of("record2", "Not found"));
		DeleteRecordsException exception = new DeleteRecordsException(notDeleted);

		ResponseEntity response = this.sut.handleDeleteRecordsException(exception);

		assertEquals(207, response.getStatusCode().value());
		assertNotNull(response.getBody());
		String body = response.getBody().toString();
		assertTrue(body.contains("record1"));
		assertTrue(body.contains("record2"));
	}

	@Test
	public void should_returnBadRequest_when_ConstraintViolationExceptionIsCaptured() {
		Set<ConstraintViolation<?>> violations = new HashSet<>();
		ConstraintViolation<?> violation = Mockito.mock(ConstraintViolation.class);
		when(violation.getMessage()).thenReturn("must not be null");
		violations.add(violation);
		ConstraintViolationException exception = new ConstraintViolationException(violations);

		ResponseEntity response = this.sut.handleConstraintValidationException(exception);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		assertNotNull(response.getBody());
	}

	@Test
	public void should_returnBadRequest_when_ConstraintViolationExceptionHasNoViolations() {
		Set<ConstraintViolation<?>> violations = new HashSet<>();
		ConstraintViolationException exception = new ConstraintViolationException(violations);

		ResponseEntity response = this.sut.handleConstraintValidationException(exception);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		String body = ((AppError) response.getBody()).getMessage();
		assertTrue(body.contains("Invalid payload"));
	}

	@Test
	public void should_returnBadRequest_when_RequestValidationExceptionIsCapturedDirectly() {
		RequestValidationException exception = RequestValidationException.builder()
				.message("Kind is invalid")
				.build();

		ResponseEntity response = this.sut.handleRequestValidationException(exception);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
	}

	@Test
	public void should_delegateToRequestValidation_when_ValidationExceptionWrapsRequestValidationException() {
		RequestValidationException cause = RequestValidationException.builder()
				.message("Kind is invalid")
				.build();
		ValidationException wrapper = new ValidationException(cause);

		ResponseEntity response = this.sut.handleValidationException(wrapper);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
	}

	@Test
	public void should_returnMethodNotAllowed_when_HttpRequestMethodNotSupportedExceptionIsCaptured() {
		HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException("DELETE");

		ResponseEntity<Object> response = this.sut.handleHttpRequestMethodNotSupported(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_METHOD_NOT_ALLOWED), Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, response.getStatusCode().value());
	}

	@Test
	public void should_returnUnsupportedMediaTypeAsAppError_when_HttpMediaTypeNotSupportedExceptionIsCaptured() {
		HttpMediaTypeNotSupportedException exception =
				new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

		ResponseEntity<Object> response = this.sut.handleHttpMediaTypeNotSupported(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE),
				Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Unsupported media type.", ((AppError) response.getBody()).getReason());
	}

	@Test
	public void should_returnNotAcceptableAsAppError_when_HttpMediaTypeNotAcceptableExceptionIsCaptured() {
		HttpMediaTypeNotAcceptableException exception =
				new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

		ResponseEntity<Object> response = this.sut.handleHttpMediaTypeNotAcceptable(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_NOT_ACCEPTABLE),
				Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Not acceptable.", ((AppError) response.getBody()).getReason());
	}

	@Test
	public void should_returnBadRequest_when_HttpMessageNotReadableWithValueInstantiationException() {
		ValueInstantiationException cause = Mockito.mock(ValueInstantiationException.class);
		HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
				"Cannot deserialize", cause, new MockHttpInputMessage(new byte[0]));

		ResponseEntity<Object> response = this.sut.handleHttpMessageNotReadable(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST), Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
	}

	@Test
	public void should_returnBadRequestWithAllViolationMessages_when_HandlerMethodValidationExceptionIsCaptured() {
		MessageSourceResolvable error1 = Mockito.mock(MessageSourceResolvable.class);
		when(error1.getDefaultMessage()).thenReturn("must not be blank");
		MessageSourceResolvable error2 = Mockito.mock(MessageSourceResolvable.class);
		when(error2.getDefaultMessage()).thenReturn("size must be between 1 and 100");

		ParameterValidationResult paramResult1 = Mockito.mock(ParameterValidationResult.class);
		when(paramResult1.getResolvableErrors()).thenReturn(List.of(error1));
		ParameterValidationResult paramResult2 = Mockito.mock(ParameterValidationResult.class);
		when(paramResult2.getResolvableErrors()).thenReturn(List.of(error2));

		HandlerMethodValidationException exception = Mockito.mock(HandlerMethodValidationException.class);
		Mockito.doReturn(List.of(paramResult1, paramResult2)).when(exception).getAllValidationResults();

		ResponseEntity<Object> response = this.sut.handleHandlerMethodValidationException(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST), Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		AppError body = (AppError) response.getBody();
		assertEquals("Validation error.", body.getReason());
		assertEquals("must not be blank; size must be between 1 and 100", body.getMessage());
	}

	@Test
	public void should_skipBlankMessages_when_HandlerMethodValidationExceptionHasBlankDefaultMessage() {
		MessageSourceResolvable blankError = Mockito.mock(MessageSourceResolvable.class);
		when(blankError.getDefaultMessage()).thenReturn(null);
		MessageSourceResolvable realError = Mockito.mock(MessageSourceResolvable.class);
		when(realError.getDefaultMessage()).thenReturn("must not be null");

		ParameterValidationResult paramResult = Mockito.mock(ParameterValidationResult.class);
		when(paramResult.getResolvableErrors()).thenReturn(List.of(blankError, realError));

		HandlerMethodValidationException exception = Mockito.mock(HandlerMethodValidationException.class);
		Mockito.doReturn(List.of(paramResult)).when(exception).getAllValidationResults();

		ResponseEntity<Object> response = this.sut.handleHandlerMethodValidationException(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST), Mockito.mock(WebRequest.class));

		AppError body = (AppError) response.getBody();
		assertEquals("must not be null", body.getMessage());
	}

	@Test
	public void should_returnFallbackMessage_when_HandlerMethodValidationExceptionHasNoErrors() {
		HandlerMethodValidationException exception = Mockito.mock(HandlerMethodValidationException.class);
		Mockito.doReturn(Collections.emptyList()).when(exception).getAllValidationResults();

		ResponseEntity<Object> response = this.sut.handleHandlerMethodValidationException(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST), Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		AppError body = (AppError) response.getBody();
		assertEquals("Validation error.", body.getMessage());
	}

	@Test
	public void should_returnBadRequestAsPlainJson_when_MissingRequestHeaderExceptionIsCaptured() throws Exception {
		MissingRequestHeaderException exception = Mockito.mock(MissingRequestHeaderException.class);
		when(exception.getMessage()).thenReturn("Required request header 'x-collaboration' is not present");

		ResponseEntity<Object> response = this.sut.handleServletRequestBindingException(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST), Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Validation error.", ((AppError) response.getBody()).getReason());
	}

	@Test
	public void should_returnInternalServerErrorAsAppError_when_MissingPathVariableExceptionIsCaptured() {
		MissingPathVariableException exception = Mockito.mock(MissingPathVariableException.class);
		when(exception.getMessage()).thenReturn("Required path variable 'id' is not present");

		ResponseEntity<Object> response = this.sut.handleServletRequestBindingException(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR),
				Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Server error.", ((AppError) response.getBody()).getReason());
	}

	@Test
	public void should_returnBadRequestAsPlainJson_when_MissingServletRequestParameterExceptionIsCaptured() {
		MissingServletRequestParameterException exception =
				new MissingServletRequestParameterException("kind", "String");

		ResponseEntity<Object> response = this.sut.handleMissingServletRequestParameter(
				exception, new HttpHeaders(), HttpStatusCode.valueOf(HttpStatus.SC_BAD_REQUEST), Mockito.mock(WebRequest.class));

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
	}

}

