package com.enterprise.openfinance.requesttopay.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PiiMaskingJsonProviderTest {

    private PiiMaskingJsonProvider provider;
    private StringWriter writer;
    private JsonGenerator generator;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        provider = new PiiMaskingJsonProvider();
        writer = new StringWriter();
        generator = new JsonFactory().createGenerator(writer);
        generator.writeStartObject();
    }

    private Map<String, Object> getResult() throws Exception {
        generator.close();
        return mapper.readValue(writer.toString(), Map.class);
    }

    @Test
    void writeTo_withSensitiveData_shouldMaskFields() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("User 'debtorId-123' requested payment from 'creditorName-abc' for amount 123.45");

        provider.writeTo(generator, event);

        Map<String, Object> result = getResult();
        String maskedMessage = (String) result.get("message");

        assertEquals("User '***MASKED***' requested payment from '***MASKED***' for amount ***MASKED***", maskedMessage
                .replaceAll("\"debtorId\":\"\\*\\*\\*MASKED\\*\\*\\*\"", "'***MASKED***'")
                .replaceAll("\"creditorName\":\"\\*\\*\\*MASKED\\*\\*\\*\"", "'***MASKED***'")
                .replaceAll("\"amount\":\\*\\*\\*MASKED\\*\\*\\*", "amount ***MASKED***"));
    }

    @Test
    void writeTo_withJsonInMessage_shouldMaskFields() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("{\"creditorName\":\"BigCorp\",\"debtorId\":\"user-5678\",\"amount\":500.00}");

        provider.writeTo(generator, event);

        Map<String, Object> result = getResult();
        String maskedMessage = (String) result.get("message");

        assertEquals("{\"creditorName\":\"***MASKED***\",\"debtorId\":\"***MASKED***\",\"amount\":\"***MASKED***\"}", maskedMessage);
    }

    @Test
    void writeTo_withNoSensitiveData_shouldNotChangeMessage() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("This is a regular log message without any sensitive data.");

        provider.writeTo(generator, event);

        Map<String, Object> result = getResult();
        String message = (String) result.get("message");

        assertEquals("This is a regular log message without any sensitive data.", message);
    }
}
