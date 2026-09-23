package com.nordicframtiden.chat;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/calls/history")
public class CallHistoryController {
  private final CallHistoryService service;
  CallHistoryController(CallHistoryService service) { this.service = service; }

  @GetMapping
  public List<CallHistoryService.Item> list(Authentication authentication) {
    return service.list(authentication.getName());
  }
}
