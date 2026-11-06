package com.first.genProject.config;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Only account-independent public files may be cached. APIs and private HTML keep security defaults. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 30)
public class PublicAssetCacheFilter extends OncePerRequestFilter {
    private static final Set<String> PAGES=Set.of("/", "/index.html", "/login.html", "/privacy.html");
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!Set.of("GET","HEAD").contains(request.getMethod()) || request.getHeader("Authorization")!=null
                || request.getHeader("Range")!=null) return true;
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean publicFile=PAGES.contains(path) || path.matches("/(?:ui/)?[A-Za-z0-9_-]+\\.(?:js|css|svg|ico)");
        String resource=path.equals("/")?"/index.html":path;
        return !publicFile || !new ClassPathResource("static"+resource).exists();
    }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        var captured=new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request,captured);
            if (captured.getStatus()==200 && !captured.containsHeader("Set-Cookie")) {
                String path=request.getRequestURI().substring(request.getContextPath().length());
                int ttl=PAGES.contains(path)?3600:86400;
                // Browsers revalidate stable asset URLs; Vercel can serve its cached copy.
                captured.setHeader("Cache-Control","public, max-age=0, must-revalidate");
                captured.setHeader("Vercel-CDN-Cache-Control","public, max-age="+ttl+", stale-while-revalidate=86400");
                captured.setHeader("Pragma",null);captured.setHeader("Expires",null);
            }
        } finally { captured.copyBodyToResponse(); }
    }
}
