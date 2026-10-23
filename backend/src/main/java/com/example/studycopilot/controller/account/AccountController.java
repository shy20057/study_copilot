package com.example.studycopilot.controller.account;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.auth.ChangePasswordRequest;
import com.example.studycopilot.dto.req.auth.DeleteAccountRequest;
import com.example.studycopilot.dto.req.auth.UpdateProfileRequest;
import com.example.studycopilot.service.account.AccountDeletionService;
import com.example.studycopilot.service.account.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accounts;
    private final AccountDeletionService deletions;

    @GetMapping("/profile")
    public Result<AccountService.Profile> profile() {
        return Result.success(accounts.profile());
    }

    @PutMapping("/profile")
    public Result<AccountService.Profile> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(accounts.updateProfile(request));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accounts.changePassword(request);
        return Result.success();
    }

    @PostMapping("/deletion")
    public ResponseEntity<Result<AccountDeletionService.DeletionReceipt>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request) {
        return ResponseEntity.accepted().body(Result.success(deletions.request(request)));
    }
}
