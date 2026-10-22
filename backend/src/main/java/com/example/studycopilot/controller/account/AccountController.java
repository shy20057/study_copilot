package com.example.studycopilot.controller.account;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.auth.ChangePasswordRequest;
import com.example.studycopilot.service.account.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accounts;

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accounts.changePassword(request);
        return Result.success();
    }
}
