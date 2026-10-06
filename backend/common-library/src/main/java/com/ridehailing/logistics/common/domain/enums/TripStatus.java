package com.ridehailing.logistics.common.domain.enums;

public enum TripStatus {
  CREATED,
  MATCHING,
  ACCEPTED,
  PICKING_UP,
  ARRIVED,
  IN_TRIP,
  COMPLETED,
  CANCELLED,
  NO_DRIVER;

  public boolean isActive() {
    return this == ACCEPTED || this == PICKING_UP || this == ARRIVED || this == IN_TRIP;
  }

  public boolean isTerminal() {
    return this == COMPLETED || this == CANCELLED || this == NO_DRIVER;
  }
}
