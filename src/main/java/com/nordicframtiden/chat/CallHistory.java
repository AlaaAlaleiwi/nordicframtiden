package com.nordicframtiden.chat;

import com.nordicframtiden.security.model.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_history")
class CallHistory {
  @Id @Column(name = "call_id") private UUID callId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id")
  private ChatRoom room;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "caller_id")
  private AppUser caller;
  @Column(name = "started_at", nullable = false) private Instant startedAt = Instant.now();
  @Column(name = "answered_at") private Instant answeredAt;
  @Column(name = "ended_at") private Instant endedAt;
  @Column(nullable = false, length = 20) private String outcome = "RINGING";
  @Column(nullable = false) private boolean video = false;

  public UUID getCallId() { return callId; }
  public void setCallId(UUID callId) { this.callId = callId; }
  public ChatRoom getRoom() { return room; }
  public void setRoom(ChatRoom room) { this.room = room; }
  public AppUser getCaller() { return caller; }
  public void setCaller(AppUser caller) { this.caller = caller; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getAnsweredAt() { return answeredAt; }
  public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
  public Instant getEndedAt() { return endedAt; }
  public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
  public String getOutcome() { return outcome; }
  public void setOutcome(String outcome) { this.outcome = outcome; }
  public boolean isVideo() { return video; }
  public void setVideo(boolean video) { this.video = video; }
}
