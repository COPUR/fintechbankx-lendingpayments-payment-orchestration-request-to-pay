package com.enterprise.openfinance.requesttopay.infrastructure.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiiMaskingJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

    private static final String MASK = "***MASKED***";
    // This regex looks for specific field names in the JSON and captures their values to be replaced.
    // It handles string, numeric, and boolean values.
    private static final Pattern PII_PATTERN = Pattern.compile(
            "(\"creditorName\"|\"debtorId\"|\"psuId\"|\"debtorIdentifier\"|\"amount\"|\"currency\"):(\"[^\"]*\"|[0-9.-]+|true|false)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String formattedMessage = event.getFormattedMessage();
        String maskedMessage = mask(formattedMessage);
        JsonWritingUtils.writeStringField(generator, "message", maskedMessage);
    }

    private String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        Matcher matcher = PII_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder(message.length());
        while (matcher.find()) {
            // The first group is the field name (e.g., "creditorName"), the second is the value.
            // We replace the value part with our mask.
            matcher.appendReplacement(sb, matcher.group(1) + ":\"" + MASK + "\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
