package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder; // Import this
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer; // Import this

@SpringBootApplication
public class DemoApplication extends SpringBootServletInitializer { // Extend SpringBootServletInitializer

    @Override // Override this configure method
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        // Point to your main application class
        return application.sources(DemoApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
