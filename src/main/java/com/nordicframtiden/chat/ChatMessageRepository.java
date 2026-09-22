package com.nordicframtiden.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
  List<ChatMessage> findByRoomIdAndParentIsNullOrderByIdDesc(Long roomId, Pageable pageable);
  List<ChatMessage> findByParentIdOrderByIdAsc(Long parentId);

  @Query("select count(m) from ChatMessage m where m.parent is null and m.room.id = :roomId and m.id > :afterId and m.sender.id <> :userId")
  long countUnread(@Param("roomId") Long roomId, @Param("afterId") Long afterId, @Param("userId") Long userId);

  long countByParentId(Long parentId);
}
