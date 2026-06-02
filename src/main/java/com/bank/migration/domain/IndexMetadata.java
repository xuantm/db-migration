package com.bank.migration.domain;

import java.util.List;

public record IndexMetadata(String name, boolean unique, List<String> columns) {}
