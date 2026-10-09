package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
@ContextConfiguration(classes = WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkspaceService workspaceService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void createFolderRejectsBlankPath() throws Exception {
        mockMvc.perform(
                post("/api/projects/1/workspace/folder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": " "
                                }
                                """)
        )
        .andExpect(status().isBadRequest());
    }


    @Test
    void importRejectsTooManyFiles() throws Exception {
        StringBuilder files = new StringBuilder("[");
        for (int i = 0; i < 501; i++) {
            if (i > 0) {
                files.append(",");
            }
            files.append("""
                    {"path":"file-%d.txt","name":"file-%d.txt","content":"x"}
                    """.formatted(i, i));
        }
        files.append("]");

        mockMvc.perform(
                post("/api/projects/1/workspace/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"files":%s,"folders":[]}
                                """.formatted(files))
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void importRejectsFolderPathOverOneThousandCharacters() throws Exception {
        String folder = "a".repeat(1001);
        mockMvc.perform(
                post("/api/projects/1/workspace/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(java.util.Map.of("files", java.util.List.of(), "folders", java.util.List.of(folder))))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void saveFileRejectsBlankPath() throws Exception {
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/projects/1/workspace/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "",
                                  "content": "hello"
                                }
                                """)
        )
        .andExpect(status().isBadRequest());
    }
    @Test
    void saveFileRejectsContentOverFiveMiBByUtf8Bytes() throws Exception {
        String content = "é".repeat(2_621_441);
        String escaped = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(content);
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/projects/1/workspace/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"src/Test.java\",\"content\":" + escaped + "}")
        ).andExpect(status().isBadRequest());
    }

}
