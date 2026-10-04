package com.example.starter.memo.api;

import com.example.starter.memo.domain.Memo;
import com.example.starter.memo.domain.MemoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memos")
public class MemoController {

  private final MemoService memoService;

  public MemoController(MemoService memoService) {
    this.memoService = memoService;
  }

  @PostMapping
  public ResponseEntity<MemoResponse> create(@Valid @RequestBody CreateMemoRequest request) {
    Memo memo = memoService.create(request.title(), request.content());
    return ResponseEntity.created(URI.create("/api/v1/memos/" + memo.id()))
        .body(MemoResponse.from(memo));
  }

  @GetMapping("/{id}")
  public MemoResponse get(@PathVariable long id) {
    return MemoResponse.from(memoService.get(id));
  }

  @GetMapping
  public List<MemoResponse> list() {
    return memoService.list().stream().map(MemoResponse::from).toList();
  }
}
