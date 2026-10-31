// Copyright 2017-2020, Schlumberger
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

import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalOtherExceptionMapper {

    private GlobalExceptionMapper mapper;

    public GlobalOtherExceptionMapper(GlobalExceptionMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneralException(Exception e) {
        // Top-level unreadable request bodies only. Do not walk the cause chain:
        // HttpMessageNotWritableException (response serialization) and nested
        // HttpMessageNotReadableException from downstream clients must stay 500.
        // GlobalExceptionMapper (HIGHEST_PRECEDENCE) already handles the common
        // Spring MVC path for HttpMessageNotReadableException; this is a narrow fallback.
        if (e instanceof HttpMessageNotReadableException) {
            String message = StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : "Invalid request.";
            return mapper.getErrorResponse(
                    new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", message, e));
        }
        return mapper.getErrorResponse(
                new AppException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Server error.",
                        "An unknown error has occurred.", e));
    }

}
