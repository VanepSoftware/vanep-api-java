package br.com.vanep.driver.event;

public sealed interface DriverReviewEvent {

  String driverToken();

  record Approved(String driverToken) implements DriverReviewEvent {}

  record Rejected(String driverToken) implements DriverReviewEvent {}
}
