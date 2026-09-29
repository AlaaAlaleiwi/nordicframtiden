package com.nordicframtiden.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TimeConfigTest {

  @Test
  void providesTheStockholmApplicationClock() {
    try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
      assertThat(context.getBean(Clock.class).getZone())
          .isEqualTo(ZoneId.of("Europe/Stockholm"));
    }
  }
}
