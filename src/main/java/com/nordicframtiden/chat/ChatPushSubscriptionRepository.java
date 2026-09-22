package com.nordicframtiden.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatPushSubscriptionRepository extends JpaRepository<ChatPushSubscription, Long> {
  Optional<ChatPushSubscription> findByFirebaseInstallationId(String firebaseInstallationId);
  void deleteByFirebaseInstallationIdAndUserId(String firebaseInstallationId, Long userId);

  @Query("""
      select subscription from ChatPushSubscription subscription
      join ChatRoomMember member on member.user = subscription.user
      where member.room.id = :roomId and subscription.user.id <> :senderId
      """)
  List<ChatPushSubscription> findForRoomExceptSender(@Param("roomId") Long roomId,
                                                     @Param("senderId") Long senderId);
}
