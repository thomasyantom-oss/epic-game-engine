package com.epic.engine.scene;

import java.util.List;

public record Scene(String id, List<TextSegment> description, List<SceneAction> actions) {}
