package com.skyflux.kiln.infra.web;

import com.skyflux.kiln.common.annotation.RawResponse;
import com.skyflux.kiln.common.result.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseBodyWrapAdviceTest {

    private ResponseBodyWrapAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new ResponseBodyWrapAdvice();
    }

    // --- sample controller for reflection-based MethodParameter ---
    static class SampleController {
        public String plainString() { return "hi"; }
        public SamplePojo plainPojo() { return new SamplePojo("x"); }
        public R<String> alreadyWrapped() { return R.ok("x"); }
        public ResponseEntity<String> responseEntity() { return ResponseEntity.ok("x"); }
        @RawResponse
        public SamplePojo rawAnnotated() { return new SamplePojo("x"); }
    }

    @RawResponse
    static class RawClassController {
        public SamplePojo returnsPojo() { return new SamplePojo("x"); }
    }

    public record SamplePojo(String value) { }

    private static MethodParameter methodParam(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method m = clazz.getDeclaredMethod(methodName);
        return new MethodParameter(m, -1);
    }

    private static ServerHttpRequest request(String path) {
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", path);
        return new ServletServerHttpRequest(raw);
    }

    private static ServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    @Test
    void supportsAnyConverter() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        Class<? extends HttpMessageConverter<?>> converter =
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class;
        assertThat(advice.supports(mp, converter)).isTrue();
    }

    @Test
    void wrapsPlainPojoInR() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        SamplePojo body = new SamplePojo("v");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/api/x"), response());

        assertThat(result).isInstanceOf(R.class);
        R<?> r = (R<?>) result;
        assertThat(r.code()).isEqualTo(0);
        assertThat(r.data()).isEqualTo(body);
    }

    @Test
    void passesThroughExistingR() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "alreadyWrapped");
        R<String> body = R.ok("x");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/api/x"), response());

        assertThat(result).isSameAs(body);
    }

    @Test
    void passesThroughResponseEntity() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "responseEntity");
        ResponseEntity<String> entity = ResponseEntity.status(HttpStatus.CREATED).body("x");

        Object result = advice.beforeBodyWrite(
                entity, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/api/x"), response());

        assertThat(result).isSameAs(entity);
    }

    @Test
    void passesThroughWhenMethodAnnotatedRawResponse() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "rawAnnotated");
        SamplePojo body = new SamplePojo("v");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/api/x"), response());

        assertThat(result).isSameAs(body);
    }

    @Test
    void passesThroughWhenClassAnnotatedRawResponse() throws Exception {
        MethodParameter mp = methodParam(RawClassController.class, "returnsPojo");
        SamplePojo body = new SamplePojo("v");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/api/x"), response());

        assertThat(result).isSameAs(body);
    }

    @Test
    void passesThroughStringReturnType() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainString");
        String body = "hello";

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.TEXT_PLAIN,
                org.springframework.http.converter.StringHttpMessageConverter.class,
                request("/api/x"), response());

        assertThat(result).isSameAs(body);
    }

    @Test
    void passesThroughActuatorPath() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        SamplePojo body = new SamplePojo("v");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/actuator/health"), response());

        assertThat(result).isSameAs(body);
    }

    @Test
    void passesThroughOpenApiPath() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        SamplePojo body = new SamplePojo("v");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/docs.json"), response());

        assertThat(result).isSameAs(body);
    }

    @Test
    void passesThroughSwaggerPath() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        SamplePojo body = new SamplePojo("v");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/swagger-ui/index.html"), response());

        assertThat(result).isSameAs(body);
    }

    // L2: path-prefix anchoring — /actuator must not match /actuatorial-fraud
    @Test
    void wrapsLookalikePathsThatOnlyShareActuatorPrefix() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        SamplePojo body = new SamplePojo("v");

        Object result = advice.beforeBodyWrite(
                body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/actuatorial-fraud"), response());

        assertThat(result).isInstanceOf(R.class);   // wrapped, not passed through
    }

    @Test
    void wrapsLookalikePathsForApiDocsAndSwagger() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        SamplePojo body = new SamplePojo("v");

        Object api = advice.beforeBodyWrite(body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/docsfake"), response());
        assertThat(api).isInstanceOf(R.class);

        Object sw = advice.beforeBodyWrite(body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/swagger-uix"), response());
        assertThat(sw).isInstanceOf(R.class);
    }

    @Test
    void exactPathsStillSkip() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");
        SamplePojo body = new SamplePojo("v");

        // exact base paths must be treated as skip
        Object act = advice.beforeBodyWrite(body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/actuator"), response());
        assertThat(act).isSameAs(body);

        Object docs = advice.beforeBodyWrite(body, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/docs"), response());
        assertThat(docs).isSameAs(body);
    }

    @Test
    void wrapsNullBody() throws Exception {
        MethodParameter mp = methodParam(SampleController.class, "plainPojo");

        Object result = advice.beforeBodyWrite(
                null, mp, MediaType.APPLICATION_JSON,
                org.springframework.http.converter.json.JacksonJsonHttpMessageConverter.class,
                request("/api/x"), response());

        assertThat(result).isInstanceOf(R.class);
        assertThat(((R<?>) result).data()).isNull();
    }
}
