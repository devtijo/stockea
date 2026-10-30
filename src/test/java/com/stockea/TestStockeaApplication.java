package com.stockea;

import org.springframework.boot.SpringApplication;

public class TestStockeaApplication {

    public static void main(String[] args) {
        SpringApplication.from(StockeaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
