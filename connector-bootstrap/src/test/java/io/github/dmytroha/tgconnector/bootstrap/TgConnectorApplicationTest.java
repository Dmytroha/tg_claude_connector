package io.github.dmytroha.tgconnector.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "connector.telegram.polling.enabled=false")
@ActiveProfiles("memory")
@AutoConfigureMockMvc
class TgConnectorApplicationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void registersAndListsSources() throws Exception {
        mvc.perform(post("/api/v1/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"https://t.me/telegram\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("@telegram"))
                .andExpect(jsonPath("$.ingestionMode").value("POLLING"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/v1/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"@telegram\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("@telegram"));
    }

    @Test
    void rejectsInvalidReference() throws Exception {
        mvc.perform(post("/api/v1/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"not a channel\"}"))
                .andExpect(status().isUnprocessableContent());
    }
}
