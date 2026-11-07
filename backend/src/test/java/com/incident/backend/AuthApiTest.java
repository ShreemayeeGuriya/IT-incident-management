package com.incident.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.backend.model.Role;
import com.incident.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.email=admin@incident.local",
        "app.admin.password=Admin@12345"
})
@AutoConfigureMockMvc
class AuthApiTest {

    private static final String PASSWORD = "Password@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepository;

    private String uniqueEmail() {
        return "user" + System.nanoTime() + "@test.com";
    }

    private String registerJson(String email, String password) {
        return "{\"name\":\"Test User\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(body);
        for (String name : new String[]{"token", "accessToken", "jwt"}) {
            if (json.hasNonNull(name)) {
                return json.get(name).asText();
            }
        }
        throw new AssertionError("No token field in login response: " + body);
    }

    private String userToken() throws Exception {
        String email = uniqueEmail();
        register(email);
        return login(email, PASSWORD);
    }

    private String adminToken() throws Exception {
        return login("admin@incident.local", "Admin@12345");
    }

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        assertFalse(userToken().isBlank());
    }

    @Test
    void registrationPersistsEmployeeRoleAndBCryptHashButKeepsLegacyApiRole() throws Exception {
        String email = uniqueEmail();
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = mapper.readTree(body);
        assertEquals("USER", response.path("user").path("role").asText());

        var user = userRepository.findByEmail(email).orElseThrow();
        assertEquals(Role.EMPLOYEE, user.getRole());
        assertNotEquals(PASSWORD, user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2"));
    }

    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordGives401() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPass@999")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidEmailOnRegisterGives400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("not-an-email", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointWithoutTokenGives401() throws Exception {
        mvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointWorksWithToken() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void normalUserCannotListUsers() throws Exception {
        mvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        mvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void userCanListOwnIncidents() throws Exception {
        mvc.perform(get("/api/incidents")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());
    }

    @Test
    void emptyIncidentBodyGives400() throws Exception {
        mvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
