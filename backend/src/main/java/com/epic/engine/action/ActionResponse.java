package com.epic.engine.action;

import com.epic.engine.panel.PanelRefresh;

import java.util.List;

public record ActionResponse(boolean success, String message, List<PanelRefresh> refreshPanels) {}
