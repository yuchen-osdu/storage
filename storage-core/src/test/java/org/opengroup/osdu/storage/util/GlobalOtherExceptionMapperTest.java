package org.opengroup.osdu.storage.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;


@ExtendWith(MockitoExtension.class)
public class GlobalOtherExceptionMapperTest {

    @InjectMocks
    private GlobalOtherExceptionMapper sut;

    @Mock
    private GlobalExceptionMapper mapper;

    @Test
    public void should_useGenericValuesInResponse_when_exceptionIsHandledByGlobalExceptionMapper() {
        Exception exception = new Exception("any message");
        AppError expectedBody = new AppError(INTERNAL_SERVER_ERROR.value(), "Server error.", "An unknown error has occurred.");

        when(mapper.getErrorResponse(any(AppException.class))).thenReturn(new ResponseEntity<>(expectedBody, INTERNAL_SERVER_ERROR));

        ResponseEntity response = this.sut.handleGeneralException(exception);
        assertEquals(500, response.getStatusCodeValue());
    }

    @Test
    public void should_returnBadRequest_when_topLevelHttpMessageNotReadableExceptionIsCaptured() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[]{'O', '_', 'w'}));
        AppError expectedBody = new AppError(BAD_REQUEST.value(), "Validation error.", "JSON parse error");

        when(mapper.getErrorResponse(any(AppException.class))).thenReturn(new ResponseEntity<>(expectedBody, BAD_REQUEST));

        ResponseEntity response = this.sut.handleGeneralException(exception);

        assertEquals(400, response.getStatusCodeValue());
        ArgumentCaptor<AppException> captor = ArgumentCaptor.forClass(AppException.class);
        verify(mapper).getErrorResponse(captor.capture());
        assertEquals(BAD_REQUEST.value(), captor.getValue().getError().getCode());
        assertEquals("Validation error.", captor.getValue().getError().getReason());
    }

    @Test
    public void should_returnServerError_when_HttpMessageNotReadableExceptionIsOnlyInCauseChain() {
        Exception exception = new RuntimeException("wrapper",
                new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0])));
        AppError expectedBody = new AppError(INTERNAL_SERVER_ERROR.value(), "Server error.", "An unknown error has occurred.");

        when(mapper.getErrorResponse(any(AppException.class))).thenReturn(new ResponseEntity<>(expectedBody, INTERNAL_SERVER_ERROR));

        ResponseEntity response = this.sut.handleGeneralException(exception);

        assertEquals(500, response.getStatusCodeValue());
    }

    @Test
    public void should_returnServerError_when_HttpMessageNotWritableExceptionIsCaptured() {
        HttpMessageNotWritableException exception = new HttpMessageNotWritableException("Failed to write response");
        AppError expectedBody = new AppError(INTERNAL_SERVER_ERROR.value(), "Server error.", "An unknown error has occurred.");

        when(mapper.getErrorResponse(any(AppException.class))).thenReturn(new ResponseEntity<>(expectedBody, INTERNAL_SERVER_ERROR));

        ResponseEntity response = this.sut.handleGeneralException(exception);

        assertEquals(500, response.getStatusCodeValue());
    }

}
