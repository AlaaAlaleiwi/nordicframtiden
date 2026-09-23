package com.nordicframtiden.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nordicframtiden.security.repo.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallSignalingRouterTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
  private final CallHistoryService history = mock(CallHistoryService.class);
  private final ChatPushNotificationService pushNotifications = mock(ChatPushNotificationService.class);
  private final AppUserRepository users = mock(AppUserRepository.class);
  private final CallSignalingRouter router = new CallSignalingRouter(mapper, members, history, pushNotifications, users);

  @Test
  void routesInviteOnlyToOtherRoomMembers() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob", "carol"));

    var route = router.route("alice", mapper.readTree("""
        {"type":"call.invite","callId":"be5194fd-af5d-46c9-b246-5c968f40b946","roomId":10}
        """));

    assertThat(route.recipients()).containsExactlyInAnyOrder("bob", "carol");
    assertThat(route.event().path("fromUsername").asText()).isEqualTo("alice");
  }

  @Test
  void rejectsUserOutsideRoom() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob"));

    assertThatThrownBy(() -> router.route("mallory", mapper.readTree("""
        {"type":"call.invite","callId":"be5194fd-af5d-46c9-b246-5c968f40b946","roomId":10}
        """))).isInstanceOf(ChatAccessDeniedException.class);
  }

  @Test
  void directsOffersOnlyToJoinedParticipants() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob"));
    String callId = "be5194fd-af5d-46c9-b246-5c968f40b946";
    router.route("alice", mapper.readTree(
        "{\"type\":\"call.invite\",\"callId\":\"" + callId + "\",\"roomId\":10}"));
    router.route("bob", mapper.readTree(
        "{\"type\":\"call.join\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    var route = router.route("alice", mapper.readTree(
        "{\"type\":\"call.offer\",\"callId\":\"" + callId
            + "\",\"roomId\":10,\"targetUsername\":\"bob\",\"sdp\":\"offer\"}"));

    assertThat(route.recipients()).containsExactly("bob");
  }

  @Test
  void routesRingingAcknowledgementBackToCaller() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob"));
    String callId = "be5194fd-af5d-46c9-b246-5c968f40b946";
    router.route("alice", mapper.readTree(
        "{\"type\":\"call.invite\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    var route = router.route("bob", mapper.readTree(
        "{\"type\":\"call.ringing\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    assertThat(route.recipients()).containsExactly("alice");
    assertThat(route.event().path("fromUsername").asText()).isEqualTo("bob");
  }

  @Test
  void declineEndsCallAndNotifiesRoomMembers() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob"));
    String callId = "be5194fd-af5d-46c9-b246-5c968f40b946";
    router.route("alice", mapper.readTree(
        "{\"type\":\"call.invite\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    var route = router.route("bob", mapper.readTree(
        "{\"type\":\"call.decline\",\"callId\":\"" + callId
            + "\",\"roomId\":10,\"message\":\"I will call you later\"}"));

    assertThat(route.recipients()).containsExactly("alice");
    assertThatThrownBy(() -> router.route("bob", mapper.readTree(
        "{\"type\":\"call.join\",\"callId\":\"" + callId + "\",\"roomId\":10}")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void oneParticipantLeavingDoesNotEndMultipartyDirectCall() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob", "carol"));
    String callId = "be5194fd-af5d-46c9-b246-5c968f40b946";
    router.route("alice", mapper.readTree(
        "{\"type\":\"call.invite\",\"callId\":\"" + callId + "\",\"roomId\":10}"));
    router.route("bob", mapper.readTree(
        "{\"type\":\"call.join\",\"callId\":\"" + callId + "\",\"roomId\":10}"));
    router.route("carol", mapper.readTree(
        "{\"type\":\"call.join\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    var leaveRoute = router.route("carol", mapper.readTree(
        "{\"type\":\"call.leave\",\"callId\":\"" + callId + "\",\"roomId\":10}"));
    var offerRoute = router.route("alice", mapper.readTree(
        "{\"type\":\"call.offer\",\"callId\":\"" + callId
            + "\",\"roomId\":10,\"targetUsername\":\"bob\",\"sdp\":\"offer\"}"));

    assertThat(leaveRoute.recipients()).containsExactlyInAnyOrder("alice", "bob");
    assertThat(offerRoute.recipients()).containsExactly("bob");
  }

  @Test
  void addedParticipantDecliningDoesNotEndExistingCall() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob", "carol"));
    String callId = "be5194fd-af5d-46c9-b246-5c968f40b946";
    router.route("alice", mapper.readTree(
        "{\"type\":\"call.invite\",\"callId\":\"" + callId + "\",\"roomId\":10}"));
    router.route("bob", mapper.readTree(
        "{\"type\":\"call.join\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    var declineRoute = router.route("carol", mapper.readTree(
        "{\"type\":\"call.decline\",\"callId\":\"" + callId + "\",\"roomId\":10}"));
    var offerRoute = router.route("alice", mapper.readTree(
        "{\"type\":\"call.offer\",\"callId\":\"" + callId
            + "\",\"roomId\":10,\"targetUsername\":\"bob\",\"sdp\":\"offer\"}"));

    assertThat(declineRoute.recipients()).containsExactlyInAnyOrder("alice", "bob");
    assertThat(offerRoute.recipients()).containsExactly("bob");
  }

  @Test
  void channelMemberCanDiscoverAndRejoinCallAfterLeaving() throws Exception {
    when(members.findUsernamesByRoomId(10L)).thenReturn(List.of("alice", "bob"));
    String callId = "be5194fd-af5d-46c9-b246-5c968f40b946";
    router.route("alice", mapper.readTree(
        "{\"type\":\"call.invite\",\"callId\":\"" + callId
            + "\",\"roomId\":10,\"isChannel\":true}"));
    router.route("bob", mapper.readTree(
        "{\"type\":\"call.join\",\"callId\":\"" + callId + "\",\"roomId\":10}"));
    router.route("bob", mapper.readTree(
        "{\"type\":\"call.leave\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    var activeCall = router.activeChannelCalls("bob").getFirst();
    var rejoinRoute = router.route("bob", mapper.readTree(
        "{\"type\":\"call.join\",\"callId\":\"" + callId + "\",\"roomId\":10}"));

    assertThat(activeCall.callId().toString()).isEqualTo(callId);
    assertThat(activeCall.participants()).containsExactly("alice");
    assertThat(rejoinRoute.recipients()).containsExactly("alice");
  }
}
