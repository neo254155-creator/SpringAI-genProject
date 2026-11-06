package com.first.genProject.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound JSON before deserialization, including chunked bodies and requests without retry keys. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class InputLengthFilter extends OncePerRequestFilter {
    public static final int MAX_JSON_BYTES=131072;
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type=request.getContentType();
        if (type==null) return true;
        type=type.split(";",2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        return !(type.equals("application/json") || type.startsWith("application/") && type.endsWith("+json"));
    }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        if(request.getContentLengthLong()>MAX_JSON_BYTES){reject(response);return;}
        byte[] body=request.getInputStream().readNBytes(MAX_JSON_BYTES+1);
        if(body.length>MAX_JSON_BYTES){reject(response);return;}
        chain.doFilter(new HttpServletRequestWrapper(request){
            public ServletInputStream getInputStream(){
                var input=new ByteArrayInputStream(body);
                return new ServletInputStream(){
                    public int read(){return input.read();}
                    public boolean isFinished(){return input.available()==0;}
                    public boolean isReady(){return true;}
                    public void setReadListener(ReadListener listener){
                        try{if(isFinished())listener.onAllDataRead();else listener.onDataAvailable();}
                        catch(IOException error){listener.onError(error);}
                    }
                };
            }
            public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },response);
    }
    private void reject(HttpServletResponse response)throws IOException{
        com.first.genProject.auth.AccountSecurity.problem(response,413,"INPUT_TOO_LARGE","JSON input exceeds 128 KiB. Shorten the input.");
    }
}
