package com.epic.engine.action;

import com.epic.engine.debug.GameEventLog;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/action")
public class ActionController {

    private final Map<String, ActionHandler> handlers;
    private final GameEventLog eventLog;

    public ActionController(List<ActionHandler> handlerList, GameEventLog eventLog) {
        this.handlers = handlerList.stream()
                .collect(java.util.stream.Collectors.toMap(ActionHandler::getType, h -> h));
        this.eventLog = eventLog;
    }

    @PostMapping
    public ActionResponse performAction(@RequestBody ActionRequest request) {
        ActionHandler handler = handlers.get(request.type());
        if (handler == null) {
            eventLog.logEvent(request.playerId(), request.type(), request.params().toString(), "FAIL: unknown type");
            return new ActionResponse(false, "未知的操作类型: " + request.type(), List.of());
        }
        ActionResponse response = handler.handle(request.playerId(), request.params());
        String result = response.success() ? "SUCCESS" : "FAIL: " + response.message();
        eventLog.logEvent(request.playerId(), request.type(), request.params().toString(), result);
        return response;
    }

    public record ActionRequest(String playerId, String type, Map<String, String> params) {}
}
