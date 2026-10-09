package com.codebreak.backend.file;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectFileController.class)
@ContextConfiguration(classes = ProjectFileController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectFileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectFileService fileService;

    @Test
    void createFileRejectsBlankName() throws Exception {
        mockMvc.perform(
                post("/api/projects/1/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "path": "src/Main.java",
                                  "content": "hello"
                                }
                                """)
        ).andExpect(status().isBadRequest());
    }

    @Test
    void importRejectsMoreThanFiveHundredFiles() throws Exception {
        StringBuilder files = new StringBuilder("[");
        for (int i = 0; i < 501; i++) {
            if (i > 0) {
                files.append(",");
            }
            files.append("""
                    {"name":"f%d","path":"f%d.txt","content":""}
                    """.formatted(i, i));
        }
        files.append("]");

        mockMvc.perform(
                post("/api/projects/1/files/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"files":%s,"folders":[]}
                                """.formatted(files))
        ).andExpect(status().isBadRequest());
    }
}
