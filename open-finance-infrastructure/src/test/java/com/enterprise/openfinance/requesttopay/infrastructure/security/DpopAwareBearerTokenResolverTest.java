package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DpopAwareBearerTokenResolverTest {

    private final DpopAwareBearerTokenResolver resolver = new DpopAwareBearerTokenResolver();

    @Test
    void resolvesBothBearerAndDpopSchemes() {
        assertThat(resolver.resolve(withAuthorization("Bearer abc.def.ghi"))).isEqualTo("abc.def.ghi");
        assertThat(resolver.resolve(withAuthorization("DPoP abc.def.ghi"))).isEqualTo("abc.def.ghi");
        assertThat(resolver.resolve(withAuthorization("DPoP   "))).isNull();
        assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
    }

    private static MockHttpServletRequest withAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", value);
        return request;
    }
}
