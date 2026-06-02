package com.bank.migration.preflight;

public record PreflightCheck(String name, boolean passed, String message) {}
