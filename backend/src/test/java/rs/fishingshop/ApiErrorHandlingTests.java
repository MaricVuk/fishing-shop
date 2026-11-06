package rs.fishingshop;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import rs.fishingshop.catalog.CatalogService;

@AutoConfigureMockMvc
class ApiErrorHandlingTests extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CatalogService catalog;

    @Test
    void unexpectedFailuresBecomeAGenericProblemDetail() throws Exception {
        when(catalog.listCategories()).thenThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(get("/api/categories"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }

    @Test
    void unknownRoutesAreProblemDetails() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
