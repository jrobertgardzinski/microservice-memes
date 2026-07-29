package com.jrobertgardzinski.memes.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refusal an upload gets for ANNOUNCING more than this service accepts.
 *
 * <p>The filter shipped on 2026-07-28 with no test at all, which is worth stating plainly: it exists
 * to turn a "500 internal error" into a truthful 413, and an untested error path is exactly the kind
 * that quietly stops working.
 *
 * <p>Driven directly rather than through the application, because that is what it is — a servlet
 * filter with one decision. Writing it as a full {@code @SpringBootTest} taught something useful
 * first, though: {@link RequireSignInFilter} runs BEFORE this one, so an anonymous oversized upload
 * is already refused 401 without anything being spooled anywhere. This filter's job is the
 * authenticated caller — the one who got past the gate and then declared twenty megabytes.
 */
class RejectOversizedUploadFilterTest {

    private final RejectOversizedUploadFilter filter = new RejectOversizedUploadFilter("10MB");

    /**
     * A request that DECLARES a length without carrying one.
     *
     * <p>Exactly the shape being defended against, and the reason the filter reads
     * {@code getContentLengthLong()} rather than measuring a body: the refusal has to happen on the
     * announcement, before a single byte is spooled anywhere. (MockHttpServletRequest derives its
     * content length from actual content, so declaring twenty megabytes here would mean allocating
     * them — which is the very thing under test.)
     */
    private static MockHttpServletRequest declaring(String method, long contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/memes") {
            @Override
            public long getContentLengthLong() {
                return contentLength;
            }
        };
        request.setContentType("multipart/form-data; boundary=x");
        return request;
    }

    @Test
    @DisplayName("a POST that declares more than the limit is refused with 413 and never reaches the chain")
    void an_oversized_declaration_is_refused_before_the_chain() throws Exception {
        MockHttpServletRequest request = declaring("POST", 20L * 1024 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOO_LARGE"),
                "the body names the refusal so a UI can act on it: " + response.getContentAsString());
        // the decisive assertion: refusing must happen BEFORE anything downstream sees the request,
        // so the multipart is never spooled to the container's disk on its way to being rejected
        assertNull(chain.getRequest(), "an oversized upload must not reach the chain at all");
    }

    @Test
    @DisplayName("an upload within the limit is none of this filter's business")
    void a_normal_upload_passes_through() throws Exception {
        MockHttpServletRequest request = declaring("POST", 64);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest(), "it goes on to the rest of the chain untouched");
    }

    @Test
    @DisplayName("a GET is never judged on its length, however it is announced")
    void reads_are_not_uploads() throws Exception {
        MockHttpServletRequest request = declaring("GET", 20L * 1024 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }
}
