package com.codebreak.backend.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private ProjectMemberService projectMemberService;

    @Test
    void createProjectRejectsBlankName() throws Exception {
        mockMvc.perform(
                post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "description": "test"
                                }
                                """)
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void joinProjectRejectsBlankCode() throws Exception {
        mockMvc.perform(
                post("/api/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "joinCode": ""
                                }
                                """)
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void joinProjectRejectsOversizedCode() throws Exception {
        mockMvc.perform(
                post("/api/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "joinCode": "123456789"
                                }
                                """)
        )
        .andExpect(status().isBadRequest());
    }
}
