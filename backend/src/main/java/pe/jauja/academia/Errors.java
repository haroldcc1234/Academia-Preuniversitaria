package pe.jauja.academia;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getReason() == null ? "Solicitud inválida" : e.getReason()));
    }

    @ExceptionHandler({ IllegalArgumentException.class, java.time.DateTimeException.class })
    ResponseEntity<?> bad(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage() == null ? "Datos inválidos" : e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e) {
        return ResponseEntity.status(409)
                .body(Map.of("error", "Registro duplicado o relacionado con otros datos. Revisa la información."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception e) {
        org.slf4j.LoggerFactory.getLogger(Errors.class).error("Error de aplicación", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "No se completó la operación. Conserva tus datos y vuelve a intentar."));
    }
}
