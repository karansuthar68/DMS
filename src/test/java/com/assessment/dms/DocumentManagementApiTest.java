package com.assessment.dms;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the real security filter chain, using the seeded demo users
 * (admin, john, jane) and an in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentManagementApiTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------- Authentication ----------

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"john\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Roles ----------

    @Test
    void onlyAdminCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(login("john", "John@1234"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(login("admin", "Admin@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("john")));
    }

    // ---------- File handling ----------

    @Test
    void uploadRejectsUnsupportedFileType() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents").file(exe)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login("john", "John@1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not supported")));
    }

    @Test
    void uploadAcceptsMultipleFilesInOneRequest() throws Exception {
        String johnToken = login("john", "John@1234");
        MockMultipartFile pdf = new MockMultipartFile("files", "a.pdf", null, "a".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile png = new MockMultipartFile("files", "b.png", null, "b".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile xlsx = new MockMultipartFile("files", "c.xlsx", null, "c".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents").file(pdf).file(png).file(xlsx)
                        .param("category", "Batch")
                        .header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("a.pdf"))
                .andExpect(jsonPath("$[2].category").value("Batch"));
    }

    @Test
    void multiFileUploadIsRejectedEntirelyWhenOneFileIsInvalid() throws Exception {
        String johnToken = login("john", "John@1234");
        MockMultipartFile pdf = new MockMultipartFile("files", "only-valid.pdf", null, "a".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile exe = new MockMultipartFile("files", "virus.exe", null, "b".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents").file(pdf).file(exe)
                        .header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/documents").param("keyword", "only-valid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void uploadWithoutFileReturnsValidationError() throws Exception {
        mockMvc.perform(multipart("/api/documents").param("title", "No file")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login("john", "John@1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.files").value("at least one file is required"));
    }

    @Test
    void ownerCanUploadViewDownloadAndReplaceFile() throws Exception {
        String johnToken = login("john", "John@1234");
        long documentId = upload(johnToken, "report.pdf", "pdf-content");

        mockMvc.perform(get("/api/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.ownerUsername").value("john"));

        mockMvc.perform(get("/api/documents/{id}/download", documentId).header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().string("pdf-content"));

        MockMultipartFile newVersion = new MockMultipartFile("file", "report-v2.docx", null,
                "docx-content".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}/file", documentId).file(newVersion)
                        .header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("report-v2.docx"))
                .andExpect(jsonPath("$.fileExtension").value("docx"));

        mockMvc.perform(get("/api/documents/{id}/view", documentId).header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(content().string("docx-content"));
    }

    // ---------- Document access control ----------

    @Test
    void sharedUserGetsOnlyTheGrantedAccessLevel() throws Exception {
        String johnToken = login("john", "John@1234");
        String janeToken = login("jane", "Jane@1234");
        long documentId = upload(johnToken, "salary-sheet.xlsx", "xlsx-content");

        // Not shared yet
        mockMvc.perform(get("/api/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isForbidden());

        grant(johnToken, documentId, "jane", "VIEW");
        mockMvc.perform(get("/api/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/documents/{id}/view", documentId).header(HttpHeaders.AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/documents/{id}/download", documentId).header(HttpHeaders.AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isForbidden());

        grant(johnToken, documentId, "jane", "DOWNLOAD");
        mockMvc.perform(get("/api/documents/{id}/download", documentId).header(HttpHeaders.AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isOk());

        // Shared users can read but never modify
        mockMvc.perform(delete("/api/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/documents/{id}/permissions/access", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(janeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"accessLevel\":\"VIEW\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents").param("keyword", "salary").header(HttpHeaders.AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) documentId)));
    }

    @Test
    void adminCanAccessAnyDocument() throws Exception {
        long documentId = upload(login("john", "John@1234"), "private-notes.png", "png-content");

        mockMvc.perform(get("/api/documents/{id}/download", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login("admin", "Admin@123"))))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanUpdateAndDeleteDocument() throws Exception {
        String johnToken = login("john", "John@1234");
        long documentId = upload(johnToken, "policy.pptx", "pptx-content");

        mockMvc.perform(put("/api/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"HR Policy 2026","category":"HR","metadata":{"department":"HR","version":"2"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HR Policy 2026"))
                .andExpect(jsonPath("$.metadata.department").value("HR"));

        // A shared document can still be deleted: its permission rows are removed by ON DELETE CASCADE
        grant(johnToken, documentId, "jane", "VIEW");
        mockMvc.perform(delete("/api/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(johnToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sortingByUnknownFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/documents").param("sort", "storedFileName")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login("john", "John@1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot sort by 'storedFileName'")));
    }

    @Test
    void missingDocumentReturns404() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer(login("admin", "Admin@123"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Document not found with id: 999999"));
    }

    // ---------- Domain restriction ----------

    @Test
    void requestFromUnapprovedOriginIsBlocked() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil-site.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"john\",\"password\":\"John@1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("evil-site.com")));
    }

    @Test
    void requestFromApprovedOriginIsAllowed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"john\",\"password\":\"John@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void requestToUnapprovedHostIsBlocked() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setServerName("10.0.0.5");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"john\",\"password\":\"John@1234\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- Helpers ----------

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private long upload(String token, String fileName, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", fileName, null, content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/documents").file(file)
                        .param("category", "Test")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$[0].id")).longValue();
    }

    private void grant(String ownerToken, long documentId, String username, String accessLevel) throws Exception {
        mockMvc.perform(post("/api/documents/{id}/permissions/access", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"accessLevel\":\"%s\"}".formatted(username, accessLevel)))
                .andExpect(status().isOk());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
