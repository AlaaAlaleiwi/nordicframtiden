package com.nordicframtiden.chat;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true")
public class FirebaseChatPushSender implements ChatPushSender {
  private static final Logger log = LoggerFactory.getLogger(FirebaseChatPushSender.class);
  private final FirebaseMessaging messaging;

  public FirebaseChatPushSender(FirebaseNotificationProperties properties) throws IOException {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .setProjectId(properties.getProjectId())
        .build();
    FirebaseApp app = FirebaseApp.getApps().stream().findFirst()
        .orElseGet(() -> FirebaseApp.initializeApp(options));
    this.messaging = FirebaseMessaging.getInstance(app);
  }

  @Override
  public void send(String firebaseInstallationId) {
    try {
      Message message = Message.builder()
          .setFid(firebaseInstallationId)
          .putData("title", "Nordic Framtiden Health")
          .putData("body", "You have a new message")
          .putData("url", "/chat")
          .build();
      ApiFutures.addCallback(messaging.sendAsync(message), new ApiFutureCallback<>() {
        @Override public void onSuccess(String result) {
          log.debug("Firebase chat notification completed");
        }
        @Override public void onFailure(Throwable error) {
          log.warn("Firebase chat notification could not be delivered");
        }
      }, MoreExecutors.directExecutor());
    } catch (RuntimeException error) {
      log.warn("Firebase chat notification could not be queued");
    }
  }
}
