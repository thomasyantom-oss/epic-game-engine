package com.epic.engine.combat;

import com.epic.engine.action.ActionHandler;
import com.epic.engine.action.ActionResponse;
import com.epic.engine.combat.model.CombatState;
import com.epic.engine.panel.PanelRefresh;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class StartCombatActionHandler implements ActionHandler {

    private final CombatService combatService;

    public StartCombatActionHandler(CombatService combatService) {
        this.combatService = combatService;
    }

    @Override
    public String getType() {
        return "combat";
    }

    @Override
    public ActionResponse handle(String playerId, Map<String, String> params) {
        String encounterId = params.get("target");
        if (encounterId == null || encounterId.isBlank()) {
            return new ActionResponse(false, "未指定遭遇战", List.of());
        }

        CombatState state = combatService.startCombat(playerId, encounterId);
        if (state == null) {
            return new ActionResponse(false, "遭遇战不存在: " + encounterId, List.of());
        }

        return new ActionResponse(true, null, List.of(PanelRefresh.SCENE));
    }
}
