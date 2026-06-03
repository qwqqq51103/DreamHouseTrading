package com.dreamhouse.trading.core.finmind;

import com.fasterxml.jackson.databind.JsonNode;

public record FinMindResponse(int status, String message, JsonNode data, JsonNode root) {
}
