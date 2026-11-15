package br.com.vanep.driverstudent.dto;

public record ContractStudentRowDTO(
    String itemToken,
    String dependentName,
    String dependentToken,
    String schoolToken,
    String schoolName,
    Long scheduleId) {}
