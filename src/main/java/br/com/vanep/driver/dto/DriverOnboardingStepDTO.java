package br.com.vanep.driver.dto;

import java.util.List;

public record DriverOnboardingStepDTO(boolean completed, List<String> pendingItems) {}
