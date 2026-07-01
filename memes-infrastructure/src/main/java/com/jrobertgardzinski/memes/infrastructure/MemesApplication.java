package com.jrobertgardzinski.memes.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The meme service's Spring Boot entry point. */
@SpringBootApplication
public class MemesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemesApplication.class, args);
    }
}
