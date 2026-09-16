package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitingFilterTest {

  @Test
  void skipsNonLimitedPathsAndGets() {
    RateLimitingFilter filter = new RateLimitingFilter(mock(RateLimiter.class));
    assertThat(filter.shouldNotFilter(request("GET", "/login"))).isTrue();
    assertThat(filter.shouldNotFilter(request("POST", "/api/anything"))).isTrue();
    assertThat(filter.shouldNotFilter(request("POST", "/login"))).isFalse();
  }

  @Test
  void allowsWhenUnderLimit() throws Exception {
    RateLimiter limiter = mock(RateLimiter.class);
    when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
    RateLimitingFilter filter = new RateLimitingFilter(limiter);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request("POST", "/login"), response, chain);

    verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void blocksWhenOverLimit() throws Exception {
    RateLimiter limiter = mock(RateLimiter.class);
    when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    RateLimitingFilter filter = new RateLimitingFilter(limiter);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request("POST", "/oauth2/token"), response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    verify(chain, never())
        .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void keysByRemoteAddressIgnoringForwardingHeaders() throws Exception {
    RateLimiter limiter = new RateLimiter(true, 2, 60);
    RateLimitingFilter filter = new RateLimitingFilter(limiter);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(spoofedRequest("203.0.113.1", null), new MockHttpServletResponse(), chain);
    filter.doFilter(spoofedRequest("198.51.100.7", null), new MockHttpServletResponse(), chain);
    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilter(spoofedRequest(null, "192.0.2.9"), blocked, chain);

    assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    verify(chain, org.mockito.Mockito.times(2))
        .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  private static MockHttpServletRequest spoofedRequest(String forwardedFor, String connectingIp) {
    MockHttpServletRequest request = request("POST", "/oauth2/token");
    if (forwardedFor != null) {
      request.addHeader("X-Forwarded-For", forwardedFor);
    }
    if (connectingIp != null) {
      request.addHeader("CF-Connecting-IP", connectingIp);
    }
    return request;
  }

  private static MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRequestURI(uri);
    request.setRemoteAddr("127.0.0.1");
    return request;
  }
}
