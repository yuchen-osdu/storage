/*
 * Copyright 2017-2026, The Open Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opengroup.osdu.storage.coreplus.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * CSRF is disabled because this stateless API uses explicit Bearer credentials, not browser-managed credentials.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {GSuiteSecurityConfig.class, GSuiteSecurityConfigCsrfTest.MvcConfiguration.class})
class GSuiteSecurityConfigCsrfTest {

    private static final String PATH = "/csrf-test";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    @Test
    void filterChain_installsNeitherCsrfNorBasicAuthenticationFilter() {
        var filters = securityFilterChain.getFilters(PATH);

        assertFalse(filters.stream().anyMatch(CsrfFilter.class::isInstance));
        assertFalse(filters.stream().anyMatch(BasicAuthenticationFilter.class::isInstance));
    }

    @Test
    void bearerRequestWithoutCsrfToken_isAcceptedAndCreatesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk())
                .andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void basicCredentials_areNotProcessedOrChallenged() throws Exception {
        mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Basic Zm9vOmJhcg=="))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfiguration {

        @Bean
        CsrfTestController csrfTestController() {
            return new CsrfTestController();
        }
    }

    @RestController
    static class CsrfTestController {

        @PostMapping(PATH)
        String post() {
            return "ok";
        }
    }
}
