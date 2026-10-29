package com.mpesaagent.companion.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The page behind the link shown in the chat. On a phone with the app installed the link opens the app directly
 * (app links). Otherwise this page explains what to do. It shows nothing about the request itself.
 */
@RestController
public class LandingController {

    @GetMapping(value = "/a/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> landing(@PathVariable UUID id) {
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Open your request</title>
                <style>body{font-family:system-ui,sans-serif;margin:0;padding:24px;max-width:480px;margin:auto;line-height:1.5}
                a.btn{display:block;text-align:center;padding:14px;border-radius:10px;background:#1b7f3b;color:#fff;text-decoration:none;margin:16px 0}</style>
                </head><body>
                <h1>Continue in the app</h1>
                <p>Your request is ready. Open the companion app to review it and finish in M-Pesa.</p>
                <a class="btn" href="mpesaagent://a/%s">Open the app</a>
                <p>Don't have the app yet? Install it, sign in with your phone number, then tap this link again.</p>
                <p><strong>Never share your M-Pesa PIN</strong> with anyone, in the chat or anywhere else.</p>
                </body></html>
                """.formatted(id);
        return ResponseEntity.ok(html);
    }
}
