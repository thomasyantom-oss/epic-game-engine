package com.epic.engine.scene;

import java.util.Map;

public record SceneAction(String id, String label, String type, Map<String, String> params) {}
