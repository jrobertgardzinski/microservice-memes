package com.jrobertgardzinski.memes.infrastructure.cucumber;

import com.jrobertgardzinski.memes.infrastructure.MemesApplication;
import com.jrobertgardzinski.memes.infrastructure.TestAuthConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the Spring application on a random port for Cucumber scenarios; step-defs share this
 * context. {@link TestAuthConfig} stubs the security gate, so "signed in" means presenting its
 * well-known test token.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = {MemesApplication.class, TestAuthConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {
}
