package com.codebreak.backend.folder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectFolderController.class)
@ContextConfiguration(classes = ProjectFolderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectFolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectFolderService folderService;

    @Test
    void createFolderRejectsBlankName() throws Exception {
        mockMvc.perform(
                post("/api/projects/1/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","path":"src"}
                                """)
        ).andExpect(status().isBadRequest());
    }

    @Test
    void importRejectsMoreThanFiveHundredFolders() throws Exception {
        String paths = IntStream.range(0, 501)
                .mapToObj(i -> "\"folder-" + i + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        mockMvc.perform(
                post("/api/projects/1/folders/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paths)
        ).andExpect(status().isBadRequest());
    }

    @Test
    void importRejectsOversizedFolderPath() throws Exception {
        String path = "a".repeat(1001);
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(java.util.List.of(path));

        mockMvc.perform(
                post("/api/projects/1/folders/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(status().isBadRequest());
    }
}
