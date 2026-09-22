package org.opengroup.osdu.storage.util;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Order(9)
@Component
public class CollaborationFilter extends BaseOsduFilter implements Filter {
    public static final String X_COLLABORATION_HEADER_NAME = "x-collaboration";


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        chain.doFilter(request, response);

    }
}
