package com.afternote.domain.notification.engagement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/notification-confirmations")
public class NotificationConfirmationWebController {
    private final EngagementNotificationService service;
    @GetMapping("/{token}")
    public String page(@PathVariable String token, Model model) {
        return view(token,service.link(token),model);
    }
    @PostMapping("/{token}/confirm")
    public String confirm(@PathVariable String token,Model model) {
        return view(token,service.confirm(token),model);
    }
    private String view(String token,EngagementNotificationService.LinkInfo info,Model model) {
        model.addAttribute("token",token);model.addAttribute("agent",info.purpose().equals("AGENT_CONFIRMATION"));
        model.addAttribute("confirmed",info.confirmed());
        return "notification-confirmation";
    }
}
