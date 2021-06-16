package uk.gov.ons.ssdc.exceptionmanager.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.SkippedMessage;
import uk.gov.ons.ssdc.exceptionmanager.model.entity.QuarantinedMessage;
import uk.gov.ons.ssdc.exceptionmanager.model.repository.QuarantinedMessageRepository;
import uk.gov.ons.ssdc.exceptionmanager.persistence.CachingDataStore;
import uk.gov.ons.ssdc.exceptionmanager.testutil.QueueSpy;
import uk.gov.ons.ssdc.exceptionmanager.testutil.RabbitQueueHelper;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AdminEndpointIT {
  private static final String TEST_MESSAGE_HASH =
      "9af5350f1e61149cd0bb7dfa5efae46f224aaaffed729b220d63e0fe5a8bf4b9";
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String TEST_QUEUE_NAME = "testQueue";

  @LocalServerPort private int port;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;

  @Autowired private QuarantinedMessageRepository quarantinedMessageRepository;

  @Autowired private CachingDataStore cachingDataStore;

  @Before
  public void setUp() {
    quarantinedMessageRepository.deleteAllInBatch();
    rabbitQueueHelper.purgeQueue(TEST_QUEUE_NAME);
    cachingDataStore.reset(Optional.empty());
  }

  @Test
  public void testGetBadMessages() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessages", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    Set actualResponse = objectMapper.readValue(response.getBody(), Set.class);
    assertThat(actualResponse.size()).isEqualTo(0);
  }

  @Test
  public void testGetBadMessagesMinimumSeenCount() throws Exception {
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessages?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    Set actualResponse = objectMapper.readValue(response.getBody(), Set.class);
    assertThat(actualResponse.size()).isEqualTo(0);

    // Now report a second exception
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    response =
        Unirest.get(String.format("http://localhost:%d/badmessages?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    actualResponse = objectMapper.readValue(response.getBody(), Set.class);
    assertThat(actualResponse.size()).isEqualTo(1);
    assertThat(actualResponse).containsExactly(TEST_MESSAGE_HASH);
  }

  @Test
  public void testGetBadMessage() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessage/%s", port, TEST_MESSAGE_HASH))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    List actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(0);
  }

  @Test
  public void testReplayQuarantinedMessage() throws Exception {
    try (QueueSpy testQueueSpy = rabbitQueueHelper.listen(TEST_QUEUE_NAME)) {
      SkippedMessage skippedMessage = new SkippedMessage();
      skippedMessage.setMessageHash(TEST_MESSAGE_HASH);
      skippedMessage.setQueue(TEST_QUEUE_NAME);
      skippedMessage.setContentType("application/xml");
      skippedMessage.setHeaders(Map.of("foo", "bar"));
      skippedMessage.setMessagePayload("<noodle>poodle</noodle>".getBytes());
      skippedMessage.setRoutingKey("test routing key");
      skippedMessage.setService("test service");
      skippedMessage.setSkippedTimestamp(null);

      Map<String, String> headers = new HashMap<>();
      headers.put("accept", "application/json");
      headers.put("Content-Type", "application/json");
      HttpResponse<String> response =
          Unirest.post(String.format("http://localhost:%d/storeskippedmessage", port))
              .headers(headers)
              .body(objectMapper.writeValueAsString(skippedMessage))
              .asString();

      assertThat(response.getStatus()).isEqualTo(OK.value());

      List<QuarantinedMessage> quarantinedMessages = quarantinedMessageRepository.findAll();
      assertThat(quarantinedMessages.size()).isEqualTo(1);

      UUID qmId = quarantinedMessages.get(0).getId();

      response =
          Unirest.get(String.format("http://localhost:%d/replayquarantinedmessage/%s", port, qmId))
              .headers(headers)
              .asString();

      assertThat(response.getStatus()).isEqualTo(OK.value());

      String actualMessage = testQueueSpy.checkExpectedMessageReceived();
      assertThat(actualMessage).isEqualTo("<noodle>poodle</noodle>");

      assertThat(quarantinedMessageRepository.findAll().size()).isEqualTo(0);
    }
  }

  @Test
  public void testGetBadMessageSummary() throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(String.format("http://localhost:%d/badmessages/summary", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    List actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(0);
  }

  @Test
  public void testGetBadMessageSummaryMinimumSeenCount() throws Exception {
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    Map<String, String> headers = new HashMap<>();
    headers.put("accept", "application/json");
    headers.put("Content-Type", "application/json");
    HttpResponse<String> response =
        Unirest.get(
                String.format("http://localhost:%d/badmessages/summary?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    List<Map> actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(0);

    // Now send a second exception
    ReportingEndpointIT.reportException(port, TEST_MESSAGE_HASH);

    response =
        Unirest.get(
                String.format("http://localhost:%d/badmessages/summary?minimumSeenCount=2", port))
            .headers(headers)
            .asString();

    assertThat(response.getStatus()).isEqualTo(OK.value());

    actualResponse = objectMapper.readValue(response.getBody(), List.class);
    assertThat(actualResponse.size()).isEqualTo(1);
    assertThat(actualResponse.get(0).get("messageHash")).isEqualTo(TEST_MESSAGE_HASH);
    assertThat(actualResponse.get(0).get("seenCount")).isEqualTo(2);
  }
}
