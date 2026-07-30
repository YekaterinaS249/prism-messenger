package com.example.messenger.controller;

import com.example.messenger.dto.LinkPreviewDto;
import com.example.messenger.service.LinkPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/link-preview")
public class LinkPreviewController {

    private final LinkPreviewService linkPreviewService;

    public LinkPreviewController(LinkPreviewService linkPreviewService) {
        this.linkPreviewService = linkPreviewService;
    }

    @GetMapping
    public ResponseEntity<?> preview(@RequestParam String url) {
        LinkPreviewDto dto = linkPreviewService.fetch(url);
        if (dto == null) {
            return ResponseEntity.ok(Map.of("available", false));
        }
        return ResponseEntity.ok(dto);
    }
}
