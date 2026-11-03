package pe.jauja.academia;

import java.util.*;
import java.security.*;
import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.*;

@Configuration
public class Security {
    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Bean
    PasswordEncoder passwords() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity h, Store db, @Value("${app.cors}") String origins) throws Exception {
        var c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.asList(origins.split(",")));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        c.setExposedHeaders(List.of("Content-Disposition"));
        var cs = new UrlBasedCorsConfigurationSource();
        cs.registerCorsConfiguration("/**", c);
        h.cors(x -> x.configurationSource(cs)).csrf(x -> x.disable())
                .sessionManagement(x -> x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(x -> x.requestMatchers("/api/auth/login", "/api/health").permitAll()
                        .requestMatchers("/api/ai/status").authenticated()
                        .requestMatchers("/api/ai/**", "/api/vocational/**", "/api/reports/vocational/**", "/api/challenges/**", "/api/bulletin-attempts/**", "/api/practice/questions").hasRole("ALUMNO")
                        .requestMatchers("/api/bulletins/**", "/api/files/bulletin/**", "/api/bulletin-questions/**", "/api/reports/cards/**").hasAnyRole("DIRECTOR", "ALUMNO")
                        .requestMatchers("/api/questions/**", "/api/grading").hasRole("DOCENTE")
                        .requestMatchers("/api/materials/**", "/api/files/material/**", "/api/resolutions/**", "/api/exams/**", "/api/attempts/**").hasAnyRole("DOCENTE", "ALUMNO")
                        .requestMatchers("/api/reports", "/api/users/**", "/api/audit", "/api/settings", "/api/cycles/**", "/api/sections/**", "/api/courses/**", "/api/enrollments/**").hasRole("DIRECTOR")
                        .requestMatchers("/api/offerings/*/students").hasAnyRole("DIRECTOR", "DOCENTE")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/grades", "/api/sessions/*/attendance").hasRole("DOCENTE")
                        .anyRequest().authenticated())
                .exceptionHandling(x -> x.authenticationEntryPoint((rq, rs, e) -> {
                    rs.setStatus(401);
                    rs.setContentType("application/json");
                    rs.getWriter().write("{\"error\":\"Inicia sesión para continuar\"}");
                }).accessDeniedHandler((rq, rs, e) -> {
                    rs.setStatus(403);
                    rs.setContentType("application/json;charset=UTF-8");
                    rs.getWriter().write("{\"error\":\"Esta función no corresponde a tu rol\"}");
                }));
        h.addFilterBefore(new OncePerRequestFilter() {
            protected void doFilterInternal(HttpServletRequest rq, HttpServletResponse rs, FilterChain f)
                    throws ServletException, IOException {
                String a = rq.getHeader("Authorization");
                if (a != null && a.startsWith("Bearer ")) {
                    var u = db.find(
                            "SELECT u.* FROM users u JOIN auth_tokens t ON t.user_id=u.id WHERE t.token_hash=? AND t.expires_at>? AND u.active=TRUE",
                            hash(a.substring(7)), Store.now());
                    if (u != null)
                        SecurityContextHolder.getContext()
                                .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + Store.s(u, "role")))));
                }
                f.doFilter(rq, rs);
            }
        }, UsernamePasswordAuthenticationFilter.class);
        return h.build();
    }
}
