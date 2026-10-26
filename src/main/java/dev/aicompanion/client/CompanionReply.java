package dev.aicompanion.client;

import java.util.List;

public record CompanionReply(String text, List<PlannedAction> actions) {
}
