package com.nordicframtiden.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChatReactionRepository extends JpaRepository<ChatReaction, ChatReaction.Id> {
  List<ChatReaction> findByMessageId(Long messageId);
  Optional<ChatReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);
}
