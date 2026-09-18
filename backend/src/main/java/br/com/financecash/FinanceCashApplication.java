package br.com.financecash;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinanceCashApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceCashApplication.class, args);
    }
}
