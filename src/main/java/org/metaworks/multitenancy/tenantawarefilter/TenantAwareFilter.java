package org.metaworks.multitenancy.tenantawarefilter;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.JWTClaimsSet;
import net.minidev.json.JSONObject;
import org.oce.garuda.multitenancy.TenantContext;
import org.springframework.http.HttpMethod;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
//import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Created by uengine on 2017. 6. 12..
 */
@WebFilter
//@Component
public class TenantAwareFilter implements Filter {

    public TenantAwareFilter(){
        setAllowAnonymousTenant(true);
    }

    boolean allowAnonymousTenant;
        public boolean isAllowAnonymousTenant() {
            return allowAnonymousTenant;
        }
        /**
         *
         * @param allowAnonymousTenant
         * enable anonymously accessing tenant that doesn't have any token information access. for testing or some purposes.
         */
        public void setAllowAnonymousTenant(boolean allowAnonymousTenant) {
            this.allowAnonymousTenant = allowAnonymousTenant;
        }


    boolean devMode;
        public boolean isDevMode() {
            return devMode;
        }
        public void setDevMode(boolean devMode) {
            this.devMode = devMode;
        }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        //String token = ((HttpServletRequest)servletRequest).getParameter("access_token");
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        if(isDevMode()){
            String userName = ((HttpServletRequest) servletRequest).getHeader("access_token");

            try {
                String tenantId = userName.split("@")[1];

                new TenantContext(tenantId);
                TenantContext.getThreadLocalInstance().setUserId(userName);

                filterChain.doFilter(servletRequest, servletResponse);

                return;
            }catch(Exception e) {
                anonymousAccess(servletRequest, servletResponse, filterChain, e);
                return;
            }
        }


        if (request.getMethod().equals(HttpMethod.OPTIONS.toString())) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            String token = ((HttpServletRequest) servletRequest).getHeader("access_token");
            //Use for add token FeignRequestInterceptor.java request template.
            new TokenContext(token);

            //java 처리 후
            String jwt = token;

            JWSObject jwsObject = null;
            String tenantId = null;
            String userName = null;
            JSONObject contexts = null;
            try {
                jwsObject = JWSObject.parse(token);

                JSONObject jsonPayload = jwsObject.getPayload().toJSONObject();
                JWTClaimsSet jwtClaimsSet = JWTClaimsSet.parse(jsonPayload);

                contexts = (JSONObject) jwtClaimsSet.getClaim("context");
                userName = (String) contexts.get("userName");

                //new User(userName);

                tenantId = userName.split("@")[1];

            } catch (Exception e) {
                //TODO:
                //throw new RuntimeException("Invalid login ", e);

                anonymousAccess(servletRequest, servletResponse, filterChain, e);
                return;
            }

            new TenantContext(tenantId);
            TenantContext.getThreadLocalInstance().setUserId(userName);

            //Provide spring security context
//            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(contexts, null, null);
//            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
//            SecurityContextHolder.getContext().setAuthentication(authentication);


            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private void anonymousAccess(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain, Exception e) throws IOException, ServletException {
        if (isAllowAnonymousTenant()) {
            new TenantContext("anonymous");
            TenantContext.getThreadLocalInstance().setUserId("anonymous");

            filterChain.doFilter(servletRequest, servletResponse);

            return;
        } else {
            throw new ServletException("Invalid Access: No tenant information", e);
        }
    }

    @Override
    public void destroy() {

    }
}
