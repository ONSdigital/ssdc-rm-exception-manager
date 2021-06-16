package uk.gov.ons.ssdc.exceptionmanager.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.ssdc.exceptionmanager.model.entity.QuarantinedMessage;

public interface QuarantinedMessageRepository extends JpaRepository<QuarantinedMessage, UUID> {}
