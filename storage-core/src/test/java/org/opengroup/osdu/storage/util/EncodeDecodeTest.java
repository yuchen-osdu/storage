package org.opengroup.osdu.storage.util;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.http.AppException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EncodeDecodeTest {

    private EncodeDecode encodeDecode;

    @BeforeEach
    public void setup(){
        encodeDecode = new EncodeDecode();
    }

    // TODO: Trufflehog preventing from pushing base64 string tests. Removed tests because of that.
    @Test
    public void should_decodeToString_postEncodingDecoding() {
        String inputString = "hello+world";

        String resultString = encodeDecode.deserializeCursor(encodeDecode.serializeCursor(inputString));
        assertEquals(inputString, resultString);
    }

    @Test
    public void should_throwError_onNonBase64Input() {
        String inputString = "invalid_cursor";
        AppException exception = assertThrows(AppException.class, ()->{
            encodeDecode.deserializeCursor(inputString);
        });
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
    }

    @Test
    public void should_returnNull_when_deserializingNullCursor() {
        assertNull(encodeDecode.deserializeCursor(null));
    }

    @Test
    public void should_returnNull_when_deserializingEmptyCursor() {
        assertNull(encodeDecode.deserializeCursor(""));
    }

    @Test
    public void should_throwError_onBlankWhitespaceInput() {
        AppException exception = assertThrows(AppException.class, ()->{
            encodeDecode.deserializeCursor("   ");
        });
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
    }

    @Test
    public void should_returnNull_when_serializingNullContinuationToken() {
        assertNull(encodeDecode.serializeCursor(null));
    }

    @Test
    public void should_returnEmptyString_when_serializingEmptyContinuationToken() {
        assertEquals("", encodeDecode.serializeCursor(""));
    }

}
