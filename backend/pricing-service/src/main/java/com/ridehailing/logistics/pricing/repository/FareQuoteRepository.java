package com.ridehailing.logistics.pricing.repository;

import com.ridehailing.logistics.pricing.domain.entity.FareQuote;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FareQuoteRepository extends JpaRepository<FareQuote, UUID> {}
