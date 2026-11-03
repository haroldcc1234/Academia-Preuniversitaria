package pe.jauja.academia;

import java.util.*;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    final Store db;
    final Access ac;
    final PasswordEncoder pw;
    final Map<String, long[]> limits = new ConcurrentHashMap<>();

    public AuthController(Store d, Access a, PasswordEncoder p) {
        db = d;
        ac = a;
        pw = p;
    }

    @GetMapping("/health")
    public Object health() {
        return m("status", "ok");
    }

    @PostMapping("/auth/login")
    public Object login(@RequestBody Map<String, Object> x, HttpServletRequest r) {
        String email = s(x, "email").toLowerCase().trim(), key = r.getRemoteAddr() + email;
        long t = System.currentTimeMillis();
        var l = limits.computeIfAbsent(key, k -> new long[] { 0, t });
        synchronized (l) {
            if (t - l[1] > 900000) {
                l[0] = 0;
                l[1] = t;
            }
            if (l[0]++ >= 12)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Espera 15 minutos antes de volver a intentar");
        }
        var u = db.find("SELECT * FROM users WHERE email=? AND active=TRUE", email);
        if (u == null || !pw.matches(s(x, "password"), s(u, "password")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        limits.remove(key);
        String token = id() + "." + id();
        db.insert("auth_tokens", m("user_id", u.get("id"), "token_hash", Security.hash(token), "expires_at",
                Instant.now().plusSeconds(28800).toString()));
        u.remove("password");
        return m("token", token, "user", u);
    }

    @GetMapping("/auth/me")
    public Object me() {
        var u = new HashMap<>(ac.user());
        u.remove("password");
        return u;
    }

    @PostMapping("/auth/logout")
    public Object logout(HttpServletRequest r) {
        db.update("DELETE FROM auth_tokens WHERE token_hash=?",
                Security.hash(r.getHeader("Authorization").substring(7)));
        return m("ok", true);
    }

    @PutMapping("/auth/profile")
    @org.springframework.transaction.annotation.Transactional
    public Object profile(@RequestBody Map<String, Object> x) {
        if (x.containsKey("career") || x.containsKey("university")) {
            ac.roles("ALUMNO");
            db.patch("users", ac.uid(), m("career", s(x, "career"), "university", s(x, "university")));
        }
        if (!s(x, "new_password").isBlank()) {
            check(pw.matches(s(x, "current_password"), s(ac.user(), "password")), "Contraseña actual incorrecta");
            check(s(x, "new_password").length() >= 10, "Usa al menos 10 caracteres");
            db.patch("users", ac.uid(), m("password", pw.encode(s(x, "new_password"))));
            db.update("DELETE FROM auth_tokens WHERE user_id=?", ac.uid());
        }
        return m("ok", true);
    }
}
