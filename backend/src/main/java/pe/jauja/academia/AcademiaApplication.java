package pe.jauja.academia;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AcademiaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AcademiaApplication.class, args);
    }
}
