package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.project.TaakWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuratie voor WebSocket endpoints.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final TaakWebSocketHandler handler;

  public WebSocketConfig(TaakWebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/taken").setAllowedOrigins("*");
  }
}
