package jp.hairia.questionnaire.controller;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.questionnaire.service.QuestionnaireService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/questionnaires")
public class QuestionnaireController {
    private final QuestionnaireService service;

    public QuestionnaireController(QuestionnaireService service) {
        this.service = service;
    }

    @PostMapping("/answers")
    public void answer(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        service.answer(me, b);
    }
}
