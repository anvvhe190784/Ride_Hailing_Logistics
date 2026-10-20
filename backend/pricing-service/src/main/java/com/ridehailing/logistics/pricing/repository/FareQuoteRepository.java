package com.ridehailing.logistics.pricing.repository;

import com.ridehailing.logistics.pricing.domain.entity.FareQuote;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface FareQuoteRepository extends JpaRepository<FareQuote, UUID> {

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM FareQuote q WHERE q.used = false AND q.expiresAt < :now")
  int deleteExpiredUnusedQuotes(@Param("now") Instant now);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE FareQuote q SET q.used = true WHERE q.id = :quoteId AND q.customerId = :customerId AND q.used = false AND q.expiresAt > :now")
  int consumeQuoteAtomically(
      @Param("quoteId") UUID quoteId,
      @Param("customerId") UUID customerId,
      @Param("now") Instant now);
}
