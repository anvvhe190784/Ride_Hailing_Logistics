package com.ridehailing.logistics.pricing.repository;

import com.ridehailing.logistics.pricing.domain.entity.FareQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FareQuoteRepository extends JpaRepository<FareQuote, UUID> {
}
