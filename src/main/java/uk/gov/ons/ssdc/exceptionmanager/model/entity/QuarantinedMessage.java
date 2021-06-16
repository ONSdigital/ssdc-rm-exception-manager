package uk.gov.ons.ssdc.exceptionmanager.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

@Data
@Entity
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
public class QuarantinedMessage {
  @Id private UUID id;

  @Column(columnDefinition = "timestamp with time zone")
  @CreationTimestamp
  private OffsetDateTime skippedTimestamp;

  @Column private String messageHash;

  @Lob
  @Type(type = "org.hibernate.type.BinaryType")
  @Column
  private byte[] messagePayload;

  @Column private String service;

  @Column private String queue;

  @Column private String routingKey;

  @Column private String contentType;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private Map<String, JsonNode> headers;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private String errorReports;
}
