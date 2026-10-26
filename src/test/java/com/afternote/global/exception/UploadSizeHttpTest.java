package com.afternote.global.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

// MockMvc는 실제 Tomcat multipart 용량 제한을 적용하지 않으므로 HTTP로 검증한다.
@SpringBootTest(classes = UploadSizeHttpTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=4KB"})
class UploadSizeHttpTest {
    @LocalServerPort int port;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void oversizedFileOnUnknownRootReturns413BeforeRouting() throws Exception {
        assertTooLarge(send("/", part("wafjunk", 1500)));
    }

    @Test
    void oversizedTotalRequestReturns413() throws Exception {
        StringBuilder parts = new StringBuilder();
        for (int i = 0; i < 6; i++) parts.append(part("file" + i, 800));
        assertTooLarge(send("/upload", parts.toString()));
    }

    @Test
    void uploadWithinLimitStillReachesController() throws Exception {
        var response = send("/upload", part("file", 100));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("100");
    }

    private void assertTooLarge(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        var body = mapper.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(413);
        assertThat(body.get("code").asInt()).isEqualTo(1009);
        assertThat(body.get("message").asText()).isEqualTo("업로드 허용 용량을 초과했습니다.");
        assertThat(response.body()).doesNotContain("wafjunk", "Exception", "1048576");
    }

    private HttpResponse<String> send(String path, String parts) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=upload-test-boundary")
                .POST(HttpRequest.BodyPublishers.ofString(parts + "--upload-test-boundary--\r\n",
                        StandardCharsets.UTF_8)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String part(String name, int size) {
        return "--upload-test-boundary\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"test.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                + "x".repeat(size) + "\r\n";
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            MultipartAutoConfiguration.class, JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class})
    @Import({GlobalExceptionHandler.class, UploadController.class})
    static class Config { }

    @RestController
    static class UploadController {
        @PostMapping("/upload")
        long upload(@RequestPart("file") MultipartFile file) { return file.getSize(); }
    }
}
