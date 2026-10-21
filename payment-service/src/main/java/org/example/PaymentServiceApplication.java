package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")); // avoids the Postgres "Asia/Calcutta" error
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
