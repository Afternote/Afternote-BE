package com.afternote.global.web;

import com.afternote.global.config.PasskeyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetLinksControllerTest {

    @Test
    @DisplayName("assetlinks.json 은 패키지와 지문을 내려준다")
    void assetLinks() throws Exception {
        PasskeyProperties properties = new PasskeyProperties();
        properties.setAndroidSha256("AA:BB, CC:DD");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AssetLinksController(properties)).build();

        mockMvc.perform(get("/.well-known/assetlinks.json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relation[0]").value("delegate_permission/common.get_login_creds"))
                .andExpect(jsonPath("$[0].relation[1]").value("delegate_permission/common.handle_all_urls"))
                .andExpect(jsonPath("$[0].target.package_name").value("com.afternote.afternote_fe"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]").value("AA:BB"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[1]").value("CC:DD"));
    }
}
