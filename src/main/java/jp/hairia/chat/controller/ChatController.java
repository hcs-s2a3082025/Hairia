package jp.hairia.chat.controller;

import jp.hairia.chat.service.ChatService;
import jp.hairia.common.config.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static jp.hairia.common.validation.Body.id;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/messages")
    public List<Map<String, Object>> messages(@RequestAttribute("me") CurrentUser me) {
        return service.messages(me);
    }

    @PostMapping("/messages")
    public void send(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        service.send(me, b);
    }

    @PostMapping("/read")
    public void read(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        service.read(me, id(b, "withId"));
    }
}
