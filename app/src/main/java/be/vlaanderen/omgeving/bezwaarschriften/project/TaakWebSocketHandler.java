package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieNotificatie;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakDto;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.ClusteringNotificatie;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.ClusteringTaakDto;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieNotificatie;
import be.vlaanderen.omgeving.bezwaarschriften.tekstextractie.TekstExtractieTaakDto;
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
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler die taak-updates naar verbonden clients stuurt.
 */
@Component
public class TaakWebSocketHandler extends TextWebSocketHandler
    implements ExtractieNotificatie, ConsolidatieNotificatie, ClusteringNotificatie,
    TekstExtractieNotificatie {

  private static final Logger LOG = LoggerFactory.getLogger(TaakWebSocketHandler.class);

  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper;

  public TaakWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static final int SEND_TIME_LIMIT_MS = 5000;
  private static final int BUFFER_SIZE_LIMIT = 65536;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    var concurrentSession = new ConcurrentWebSocketSessionDecorator(
        session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
    sessions.add(concurrentSession);
    LOG.info("WebSocket sessie verbonden: {}", session.getId());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.removeIf(s -> {
      if (s instanceof ConcurrentWebSocketSessionDecorator decorator) {
        return decorator.getDelegate().getId().equals(session.getId());
      }
      return s.getId().equals(session.getId());
    });
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

  @Override
  public void clusteringTaakGewijzigd(ClusteringTaakDto taak) {
    verstuur(Map.of("type", "clustering-update", "taak", taak));
  }

  @Override
  public void tekstExtractieTaakGewijzigd(TekstExtractieTaakDto taak) {
    verstuur(Map.of("type", "tekst-extractie-update", "taak", taak));
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
