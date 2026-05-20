package com.epic.engine.action;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/action")
public class ActionController {

    private final Map<String, ActionHandler> handlers;

    public ActionController(List<ActionHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(java.util.stream.Collectors.toMap(ActionHandler::getType, h -> h));
    }

    @PostMapping
    public ActionResponse performAction(@RequestBody ActionRequest request) {
        ActionHandler handler = handlers.get(request.type());
        if (handler == null) {
            return new ActionResponse(false, "未知的操作类型: " + request.type(), List.of());
        }
        return handler.handle(request.playerId(), request.params());
    }

    public record ActionRequest(String playerId, String type, Map<String, String> params) {}
}
