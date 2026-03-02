package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieNotificatie;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakDto;
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
 * WebSocket handler die taak-updates naar verbonden clients stuurt.
 */
@Component
public class TaakWebSocketHandler extends TextWebSocketHandler
    implements ExtractieNotificatie, ConsolidatieNotificatie {

  private static final Logger LOG = LoggerFactory.getLogger(TaakWebSocketHandler.class);

  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper;

  public TaakWebSocketHandler(ObjectMapper objectMapper) {
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
    verstuur(Map.of("type", "taak-update", "taak", taak));
  }

  @Override
  public void consolidatieTaakGewijzigd(ConsolidatieTaakDto taak) {
    verstuur(Map.of("type", "consolidatie-update", "taak", taak));
  }

  void verstuur(Map<String, Object> bericht) {
    String json;
    try {
      json = objectMapper.writeValueAsString(bericht);
    } catch (JsonProcessingException e) {
      LOG.error("Fout bij serialisatie van WebSocket-bericht", e);
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
