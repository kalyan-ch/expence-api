package com.expence.expenceApi.controller.advice;

import com.expence.expenceApi.AbstractIntegrationTest;
import com.expence.expenceApi.exception.ConflictException;
import com.expence.expenceApi.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandlerTest.TestControllers.class)
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @TestConfiguration
    static class TestControllers {

        @RestController
        static class TestController {

            @GetMapping("/api/test/not-found")
            public void notFound() {
                throw new NotFoundException("Thing not found");
            }

            @GetMapping("/api/test/conflict")
            public void conflict() {
                throw new ConflictException("An account named 'checking' already exists");
            }

            @GetMapping("/api/test/by-id/{id}")
            public void byId(@PathVariable UUID id) {
                // reached only when UUID parses; the handler matters when it doesn't
            }
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @Test
    void notFoundReturns404WithMessage() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Thing not found"));
    }

    @Test
    void conflictReturns409WithMessage() throws Exception {
        mockMvc.perform(get("/api/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account named 'checking' already exists"));
    }

    @Test
    void malformedUuidReturns400Not500() throws Exception {
        mockMvc.perform(get("/api/test/by-id/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
