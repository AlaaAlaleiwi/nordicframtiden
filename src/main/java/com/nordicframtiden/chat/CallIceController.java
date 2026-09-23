package com.nordicframtiden.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/calls")
class CallIceController {
  private final String turnUrls;
  private final String turnUsername;
  private final String turnCredential;

  CallIceController(
      @Value("${calls.ice.turn-urls:}") String turnUrls,
      @Value("${calls.ice.turn-username:}") String turnUsername,
      @Value("${calls.ice.turn-credential:}") String turnCredential) {
    this.turnUrls = turnUrls;
    this.turnUsername = turnUsername;
    this.turnCredential = turnCredential;
  }

  @GetMapping("/ice-servers")
  List<IceServer> iceServers() {
    var result = new ArrayList<IceServer>();
    result.add(new IceServer(List.of("stun:stun.l.google.com:19302"), null, null));
    var urls = commaSeparated(turnUrls);
    if (!urls.isEmpty() && !turnUsername.isBlank() && !turnCredential.isBlank()) {
      result.add(new IceServer(urls, turnUsername, turnCredential));
    }
    return result;
  }

  private List<String> commaSeparated(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isEmpty())
        .toList();
  }

  record IceServer(List<String> urls, String username, String credential) {}
}
