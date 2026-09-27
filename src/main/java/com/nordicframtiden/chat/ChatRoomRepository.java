package com.nordicframtiden.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
  @Query("""
      select distinct r from ChatRoom r
      join ChatRoomMember m on m.room = r
      where m.user.id = :userId
      order by r.createdAt desc
      """)
  List<ChatRoom> findVisibleTo(@Param("userId") Long userId);

  /**
   * Public channels the user has NOT joined yet — discoverable in the room
   * list/search and joinable. Private channels and other people's direct
   * messages stay invisible.
   */
  @Query("""
      select r from ChatRoom r
      where r.type = :channelType
        and r.privateChannel = false
        and not exists (select m from ChatRoomMember m where m.room = r and m.user.id = :userId)
      order by r.createdAt desc
      """)
  List<ChatRoom> findDiscoverable(@Param("userId") Long userId, @Param("channelType") ChatRoom.Type channelType);

  @Query(value = """
      select r.* from chat_room r
      where r.type = 'DIRECT'
        and (select count(*) from chat_room_member m where m.room_id = r.id) = 2
        and exists (select 1 from chat_room_member m where m.room_id = r.id and m.user_id = :firstId)
        and exists (select 1 from chat_room_member m where m.room_id = r.id and m.user_id = :secondId)
      limit 1
      """, nativeQuery = true)
  Optional<ChatRoom> findDirectBetween(@Param("firstId") Long firstId, @Param("secondId") Long secondId);

  void deleteByCreatedBy(com.nordicframtiden.security.model.AppUser createdBy);
}
