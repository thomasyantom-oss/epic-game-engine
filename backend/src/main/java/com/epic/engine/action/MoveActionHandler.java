package com.epic.engine.action;

import com.epic.engine.panel.PanelRefresh;
import com.epic.engine.save.PlayerState;
import com.epic.engine.save.PlayerStateRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MoveActionHandler implements ActionHandler {

    private final PlayerStateRepository repository;

    public MoveActionHandler(PlayerStateRepository repository) {
        this.repository = repository;
    }

    @Override
    public String getType() {
        return "move";
    }

    @Override
    public ActionResponse handle(String playerId, Map<String, String> params) {
        String target = params.get("target");
        if (target == null || target.isBlank()) {
            return new ActionResponse(false, "无效的移动目标", List.of());
        }

        Optional<PlayerState> stateOpt = repository.findByPlayerId(playerId);
        PlayerState state = stateOpt.orElseGet(() -> new PlayerState(playerId, "village_square"));
        state.setCurrentScene(target);
        repository.save(state);

        return new ActionResponse(true, null, List.of(PanelRefresh.SCENE));
    }
}
