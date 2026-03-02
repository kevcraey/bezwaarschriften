package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class TaakWebSocketHandlerTest {

  @Mock
  private WebSocketSession session;

  private TaakWebSocketHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TaakWebSocketHandler(new ObjectMapper());
  }

  @Test
  void broadcastNaarVerbondenSessies() throws Exception {
    when(session.isOpen()).thenReturn(true);
    when(session.getId()).thenReturn("test-sessie-1");
    handler.afterConnectionEstablished(session);

    var taak = new ExtractieTaakDto(
        1L, "windmolens", "bezwaar-001.txt", "bezig",
        1, "2026-02-28T10:00:00Z", "2026-02-28T10:01:00Z",
        null, null, null, false, false);

    handler.taakGewijzigd(taak);

    verify(session).sendMessage(argThat(msg -> {
      String payload = ((TextMessage) msg).getPayload();
      return payload.contains("\"type\":\"taak-update\"")
          && payload.contains("\"bestandsnaam\":\"bezwaar-001.txt\"")
          && payload.contains("\"status\":\"bezig\"");
    }));
  }

  @Test
  void broadcastNietNaarGeslotenSessies() throws Exception {
    when(session.getId()).thenReturn("test-sessie-2");
    handler.afterConnectionEstablished(session);
    handler.afterConnectionClosed(session, null);

    var taak = new ExtractieTaakDto(
        2L, "windmolens", "bezwaar-002.txt", "klaar",
        1, "2026-02-28T10:00:00Z", "2026-02-28T10:01:00Z",
        500, 7, null, false, false);

    handler.taakGewijzigd(taak);

    verify(session, never()).sendMessage(argThat(msg -> msg instanceof TextMessage));
  }

  @Test
  void broadcastConsolidatieUpdate() throws Exception {
    when(session.isOpen()).thenReturn(true);
    when(session.getId()).thenReturn("test-sessie-3");
    handler.afterConnectionEstablished(session);

    var taak = new ConsolidatieTaakDto(
        1L, "windmolens", "bezwaar-001.txt", "bezig",
        1, "2026-03-02T10:00:00Z", "2026-03-02T10:01:00Z",
        null);

    handler.consolidatieTaakGewijzigd(taak);

    verify(session).sendMessage(argThat(msg -> {
      String payload = ((TextMessage) msg).getPayload();
      return payload.contains("\"type\":\"consolidatie-update\"")
          && payload.contains("\"bestandsnaam\":\"bezwaar-001.txt\"")
          && payload.contains("\"status\":\"bezig\"");
    }));
  }
}
