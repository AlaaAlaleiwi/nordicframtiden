package com.nordicframtiden.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMember.Id> {
  boolean existsByRoomIdAndUserId(Long roomId, Long userId);
  Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
  List<ChatRoomMember> findByRoomId(Long roomId);

  @Query("select m.user.username from ChatRoomMember m where m.room.id = :roomId")
  List<String> findUsernamesByRoomId(@Param("roomId") Long roomId);
}
