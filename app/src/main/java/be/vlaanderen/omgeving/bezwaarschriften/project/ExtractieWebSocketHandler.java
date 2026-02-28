package be.vlaanderen.omgeving.bezwaarschriften.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler die extractie-taak updates naar verbonden clients stuurt.
 */
@Component
public class ExtractieWebSocketHandler extends TextWebSocketHandler
    implements ExtractieNotificatie {

  private static final Logger LOG = LoggerFactory.getLogger(ExtractieWebSocketHandler.class);

  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper;

  public ExtractieWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
    LOG.info("WebSocket sessie verbonden: {}", session.getId());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
    LOG.info("WebSocket sessie afgesloten: {} (status: {})", session.getId(), status);
  }

  @Override
  public void taakGewijzigd(ExtractieTaakDto taak) {
    String json;
    try {
      json = objectMapper.writeValueAsString(Map.of("type", "taak-update", "taak", taak));
    } catch (JsonProcessingException e) {
      LOG.error("Fout bij serialisatie van taak-update", e);
      return;
    }

    TextMessage message = new TextMessage(json);
    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(message);
        } catch (IOException e) {
          LOG.warn("Fout bij verzenden naar sessie {}: {}", session.getId(), e.getMessage());
        }
      }
    }
  }
}
