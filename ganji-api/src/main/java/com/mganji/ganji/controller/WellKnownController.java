package com.mganji.ganji.controller;

import com.mganji.ganji.config.GanjiProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Lets Android and iOS open https://.../a/ID links straight in the app. Empty until the app ids are configured. */
@RestController
public class WellKnownController {

    private final GanjiProperties props;

    public WellKnownController(GanjiProperties props) {
        this.props = props;
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> androidLinks() {
        if (props.androidPackage().isBlank() || props.androidSha256().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Object body = List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", props.androidPackage(),
                        "sha256_cert_fingerprints", List.of(props.androidSha256()))));
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> iosLinks() {
        if (props.iosAppId().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Object body = Map.of("applinks", Map.of(
                "details", List.of(Map.of(
                        "appIDs", List.of(props.iosAppId()),
                        "components", List.of(Map.of("/", "/a/*"))))));
        return ResponseEntity.ok(body);
    }
}
