package com.epic.engine.action;

import java.util.Map;

public interface ActionHandler {
    String getType();
    ActionResponse handle(String playerId, Map<String, String> params);
}
