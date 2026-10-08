package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
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
}
