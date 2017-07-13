package org.projectforge.security;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * This filter adds HTTP security headers to every response.
 * <p>
 * See https://blog.appcanary.com/2017/http-security-headers.html for details about the headers.
 */
public class SecurityHeaderFilter implements Filter
{
  @Override
  public void init(final FilterConfig filterConfig) throws ServletException
  {
    // nothing to do
  }

  @Override
  public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException
  {
    if (response instanceof HttpServletResponse) {
      final HttpServletResponse res = (HttpServletResponse) response;

      res.addHeader("X-XSS-Protection", "1; mode=block");
      res.addHeader("X-DNS-Prefetch-Control", "off");
      res.addHeader("X-Frame-Options", "SAMEORIGIN");
      res.addHeader("X-Content-Type-Options", "nosniff");

      // Content Security Policy header, see http://cspisawesome.com/
      final String cspValue = "default-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'";
      res.addHeader("Content-Security-Policy", cspValue);
      res.addHeader("X-Content-Security-Policy", cspValue);
      res.addHeader("X-WebKit-CSP", cspValue);
    }

    chain.doFilter(request, response);
  }

  @Override
  public void destroy()
  {
    // nothing to do
  }
}
