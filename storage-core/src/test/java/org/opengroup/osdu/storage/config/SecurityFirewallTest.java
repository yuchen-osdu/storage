// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.SecurityConfiguration;
import org.opengroup.osdu.storage.di.SpringConfig;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * Spring Security 7 with the storage {@code StrictHttpFirewall} and the Spring 7 trailing-slash filter.
 */
class SecurityFirewallTest {

  private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          ConfigurationPropertiesAutoConfiguration.class,
          DispatcherServletAutoConfiguration.class,
          WebMvcAutoConfiguration.class))
      .withUserConfiguration(SpringConfig.class, SecurityConfiguration.class)
      .withBean(FirewallProbeController.class);

  @Test
  void shouldRejectEncodedSlashByDefault() {
    runner.run(context -> mockMvc(context)
        .perform(get(URI.create("/records/firewall-probe/a%2Fb")))
        .andExpect(status().isBadRequest()));
  }

  @Test
  void shouldAllowEncodedSlashWhenEnabled() {
    runner.withPropertyValues("osdu.spring.config.enableEncodedSpecialCharacters=true")
        .run(context -> mockMvc(context)
            .perform(get(URI.create("/records/firewall-probe/a%2Fb")))
            .andExpect(status().isOk()));
  }

  @Test
  void shouldReachHandlerWithTrailingSlashThroughSecurityChain() {
    runner.run(context -> mockMvc(context)
        .perform(get("/records/firewall-probe/abc/"))
        .andExpect(status().isOk())
        .andExpect(content().string("abc")));
  }

  @Test
  void shouldStillDenyPathsOutsideTheAllowlist() {
    runner.run(context -> mockMvc(context)
        .perform(get("/other/"))
        .andExpect(status().isUnauthorized()));
  }

  private static MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
    UrlHandlerFilter trailingSlashFilter = new WebConfiguration().trailingSlashFilter().getFilter();
    return MockMvcBuilders.webAppContextSetup(context)
        .addFilters(trailingSlashFilter)
        .apply(springSecurity())
        .build();
  }

  @RestController
  static class FirewallProbeController {
    @GetMapping("/records/firewall-probe/{id}")
    public String get(@PathVariable("id") String id) {
      return id;
    }
  }
}
