package pe.jauja.academia;

import java.util.*;
import java.time.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class Store {
    public final JdbcTemplate jdbc;
    public final ObjectMapper json;

    public Store(JdbcTemplate j, ObjectMapper o) {
        jdbc = j;
        json = o;
    }

    public static String id() {
        return UUID.randomUUID().toString();
    }

    public static String now() {
        return Instant.now().toString();
    }

    public static Map<String, Object> m(Object... a) {
        Map<String, Object> b = new LinkedHashMap<>();
        for (int i = 0; i < a.length; i += 2)
            b.put(a[i].toString(), a[i + 1]);
        return b;
    }

    public static String s(Map<?, ?> m, String k) {
        return Objects.toString(m.get(k), "");
    }

    public static int n(Map<?, ?> m, String k, int d) {
        try {
            return Integer.parseInt(s(m, k));
        } catch (Exception e) {
            return d;
        }
    }

    public static double num(Map<?, ?> m, String k) {
        try {
            return Double.parseDouble(s(m, k));
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean b(Map<?, ?> m, String k) {
        return List.of("true", "1").contains(s(m, k).toLowerCase());
    }

    public static void check(boolean c, String message) {
        if (!c)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este registro");
    }

    public static String req(Map<?, ?> m, String k, int max) {
        String v = s(m, k).trim();
        check(!v.isEmpty() && v.length() <= max, "Revisa el campo: " + k);
        return v;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public Map<String, Object> find(String sql, Object... args) {
        var l = list(sql, args);
        return l.isEmpty() ? null : l.get(0);
    }

    public Map<String, Object> one(String sql, Object... args) {
        var r = find(sql, args);
        if (r == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado");
        return r;
    }

    public Map<String, Object> get(String table, String id) {
        return one("SELECT * FROM " + safe(table) + " WHERE id=?", id);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    private String safe(String t) {
        check(t.matches("[a-z_]+"), "Identificador inválido");
        return t;
    }

    public String insert(String table, Map<String, Object> values) {
        Map<String, Object> v = new LinkedHashMap<>(values);
        v.putIfAbsent("id", id());
        v.putIfAbsent("created_at", now());
        String columns = String.join(",", v.keySet().stream().map(this::safe).toList());
        jdbc.update("INSERT INTO " + safe(table) + " (" + columns + ") VALUES ("
                + String.join(",", Collections.nCopies(v.size(), "?")) + ")", v.values().toArray());
        return s(v, "id");
    }

    public void patch(String table, String id, Map<String, Object> values) {
        if (values.isEmpty())
            return;
        var args = new ArrayList<>(values.values());
        args.add(id);
        update("UPDATE " + safe(table) + " SET "
                + String.join(",", values.keySet().stream().map(k -> safe(k) + "=?").toList()) + " WHERE id=?",
                args.toArray());
    }

    public String json(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalArgumentException("Contenido inválido");
        }
    }

    public <T> T parse(String s, Class<T> c) {
        try {
            return json.readValue(s, c);
        } catch (Exception e) {
            throw new IllegalArgumentException("Contenido inválido");
        }
    }

    public void audit(String user, String action, String id, Object detail) {
        insert("audit", m("user_id", user, "action", action, "entity_id", id, "details", json(detail)));
    }
}
