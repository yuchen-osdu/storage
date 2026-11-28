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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class WebConfigurationTest {

  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
      .addFilters(new WebConfiguration().trailingSlashFilter().getFilter())
      .build();

  @ParameterizedTest
  @ValueSource(strings = {"/records", "/records/"})
  void shouldPreserveBodyAndQueryWithEitherTrailingSlash(String path) throws Exception {
    mvc.perform(put(path).queryParam("skipdupes", "true")
            .contentType(MediaType.APPLICATION_JSON).content("[{\"kind\":\"a:b:c:1.0.0\"}]"))
        .andExpect(status().isOk())
        .andExpect(content().string("true:[{\"kind\":\"a:b:c:1.0.0\"}]"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/records/opendes:wks:1", "/records/opendes:wks:1/"})
  void shouldMatchPathVariableEndpointWithEitherTrailingSlash(String path) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(content().string("opendes:wks:1"));
  }

  @Test
  void strictConverterShouldOnlyHandleJsonReplayRequests() {
    HttpMessageConverter<Object> converter = WebConfiguration.strictConverter();

    assertTrue(converter.canRead(ReplayRequest.class, MediaType.APPLICATION_JSON));
    assertTrue(converter.canWrite(ReplayRequest.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canRead(String.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canWrite(String.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canRead(ReplayRequest.class, MediaType.APPLICATION_XML));
    assertFalse(converter.canWrite(ReplayRequest.class, MediaType.APPLICATION_XML));
  }

  @Test
  @SuppressWarnings("unchecked")
  void configuredStrictConverterShouldComeFirstAndRejectUnknownReplayRequestFields() {
    HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer().registerDefaults();
    new WebConfiguration().configureMessageConverters(builder);
    HttpMessageConverter<Object> first = (HttpMessageConverter<Object>) builder.build().iterator().next();

    assertSame(WebConfiguration.strictConverter().getClass(), first.getClass());
    MockHttpInputMessage message = new MockHttpInputMessage(
        "{\"operation\":\"replay\",\"unknown\":true}".getBytes(StandardCharsets.UTF_8));
    message.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    assertThrows(HttpMessageNotReadableException.class, () -> first.read(ReplayRequest.class, message));
  }

  @RestController
  static class ProbeController {
    @PutMapping("/records")
    public String upsert(@RequestParam("skipdupes") String skipDupes, @RequestBody String body) {
      return skipDupes + ":" + body;
    }

    @GetMapping("/records/{id}")
    public String get(@PathVariable("id") String id) {
      return id;
    }
  }
}
