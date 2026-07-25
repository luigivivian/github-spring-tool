package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.MeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return MeResponse.anonymous();
        }
        return new MeResponse(true,
                user.getAttribute("login"),
                user.getAttribute("name"),
                user.getAttribute("avatar_url"));
    }
}
