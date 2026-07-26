package com.greatest.expenseService.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI expenseOpenApi() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Expense Service API")
                                .version("v1")
                                .description("Expense Tracking Microservice")
                );
    }
}