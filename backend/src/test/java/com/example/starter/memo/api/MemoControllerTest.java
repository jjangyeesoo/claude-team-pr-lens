package com.example.starter.memo.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.starter.common.config.TimeConfig;
import com.example.starter.common.error.ApiExceptionHandler;
import com.example.starter.memo.domain.MemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemoController.class)
@Import({MemoService.class, TimeConfig.class, ApiExceptionHandler.class})
class MemoControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void createReturns201WithLocationThatCanBeFetched() throws Exception {
    String location =
        mockMvc
            .perform(
                post("/api/v1/memos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"hello\",\"content\":\"world\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/memos/\\d+")))
            .andExpect(jsonPath("$.title").value("hello"))
            .andReturn()
            .getResponse()
            .getHeader("Location");

    mockMvc
        .perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("world"));
  }

  @Test
  void createRejectsBlankTitleWithStandardErrorFormat() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/memos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.details[0]").value(startsWith("title:")));
  }

  @Test
  void createRejectsMalformedJson() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/memos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void getRejectsNonNumericId() throws Exception {
    mockMvc
        .perform(get("/api/v1/memos/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
  }

  @Test
  void getReturns404ForUnknownMemo() throws Exception {
    mockMvc
        .perform(get("/api/v1/memos/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMO_NOT_FOUND"));
  }

  @Test
  void unknownPathReturns404WithStandardErrorFormat() throws Exception {
    mockMvc
        .perform(get("/api/v1/unknown"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void unsupportedMethodReturns405WithStandardErrorFormat() throws Exception {
    mockMvc
        .perform(delete("/api/v1/memos/1"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }
}
