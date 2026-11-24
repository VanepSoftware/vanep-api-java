package br.com.vanep.assistant.dto;

/**
 * A passenger of one shift of the linked driver, as the assistant sees it. {@code stopNumber} is
 * the order of the first pickup of the day; {@code hasFamilyInApp} is false for a passenger the
 * driver registered without a contract.
 */
public record AssistantStudentResponseDTO(
    String token, String name, int stopNumber, String placeName, boolean hasFamilyInApp) {}
