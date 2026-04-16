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
    // Mask JSON-style key/value fragments for known sensitive fields.
    private static final Pattern JSON_PII_PATTERN = Pattern.compile(
            "(\"creditorName\"|\"debtorId\"|\"psuId\"|\"debtorIdentifier\"|\"amount\"|\"currency\"):(\"[^\"]*\"|[0-9.-]+|true|false)",
            Pattern.CASE_INSENSITIVE
    );
    // Mask plain-text tokens such as debtorId-123 or creditorName-abc.
    private static final Pattern TEXT_IDENTIFIER_PATTERN = Pattern.compile(
            "\\b(?:debtorId|creditorName|psuId|debtorIdentifier)-[A-Za-z0-9_-]+\\b",
            Pattern.CASE_INSENSITIVE
    );
    // Mask amount values in phrases like "amount 123.45".
    private static final Pattern TEXT_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(\\bamount\\s+)([0-9]+(?:\\.[0-9]+)?)"
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
        Matcher matcher = JSON_PII_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder(message.length());
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + ":\"" + MASK + "\"");
        }
        matcher.appendTail(sb);

        String masked = TEXT_IDENTIFIER_PATTERN.matcher(sb.toString()).replaceAll(MASK);
        Matcher amountMatcher = TEXT_AMOUNT_PATTERN.matcher(masked);
        StringBuilder amountMasked = new StringBuilder(masked.length());
        while (amountMatcher.find()) {
            amountMatcher.appendReplacement(amountMasked, Matcher.quoteReplacement(amountMatcher.group(1) + MASK));
        }
        amountMatcher.appendTail(amountMasked);
        return amountMasked.toString();
    }
}
