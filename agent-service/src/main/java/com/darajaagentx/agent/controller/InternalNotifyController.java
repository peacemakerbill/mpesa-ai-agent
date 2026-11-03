package com.darajaagentx.agent.controller;

import com.darajaagentx.agent.service.ReplySender;
import com.darajaagentx.agent.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Lets agentx-api put a message in a customer's chat when Safaricom's answer arrives later (the result of a payout, a balance).
 * Only services that hold the internal key can reach it, because the InternalKeyFilter guards everything under /internal.
 */
@RestController
@RequestMapping("/internal/notify")
public class InternalNotifyController {

    private static final Logger log = LoggerFactory.getLogger(InternalNotifyController.class);
    private static final int MAX_IMAGE_BYTES = 2_000_000;

    /** [imageBase64] is a PNG. When it is given, [text] is its caption. */
    public record Notify(String text, String imageBase64) {
    }

    private final ReplySender sender;

    public InternalNotifyController(ReplySender sender) {
        this.sender = sender;
    }

    @PostMapping("/{phone}")
    public ResponseEntity<Void> notify(@PathVariable String phone, @RequestBody Notify body) {
        String to = Phones.normalize(phone);
        if (!Phones.isKenyanMobile(to) || body == null || (blank(body.text()) && blank(body.imageBase64()))) {
            return ResponseEntity.badRequest().build();
        }
        try {
            if (!blank(body.imageBase64())) {
                byte[] png = Base64.getDecoder().decode(body.imageBase64());
                if (png.length > MAX_IMAGE_BYTES) {
                    return ResponseEntity.badRequest().build();
                }
                sender.image(to, png, "image/png", blank(body.text()) ? null : body.text());
            } else {
                sender.notifyText(to, body.text());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.warn("Could not put a message in the chat of {}: {}", Phones.mask(to), e.getMessage());
            return ResponseEntity.status(502).build();
        }
        return ResponseEntity.noContent().build();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
