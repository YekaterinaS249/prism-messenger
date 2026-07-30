package com.example.messenger.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(Authentication authentication, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }
        Path dir = Paths.get(uploadsDir);
        Files.createDirectories(dir);

        String original = Path.of(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()).getFileName().toString();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String storedName = UUID.randomUUID() + ext;

        Path target = dir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String contentType = file.getContentType() == null ? "" : file.getContentType();
        boolean isImage = contentType.startsWith("image/");

        return ResponseEntity.ok(Map.of(
                "url", "/media/" + storedName,
                "name", original,
                "type", isImage ? "IMAGE" : "FILE",
                "contentType", contentType,
                "size", file.getSize()
        ));
    }
}
