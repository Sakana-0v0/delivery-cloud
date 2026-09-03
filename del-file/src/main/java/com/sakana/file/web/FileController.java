package com.sakana.file.web;

import com.sakana.file.dto.FileUploadVO;
import com.sakana.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "File upload, query, delete API")
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "Upload file", description = "Upload file to MinIO with auto deduplication")
    public ResponseEntity<FileUploadVO> upload(
            @Parameter(description = "File to upload") MultipartFile file) {
        log.info("File upload request: originalName={}, size={}",
                file.getOriginalFilename(), file.getSize());
        FileUploadVO result = fileService.upload(file);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/info")
    @Operation(summary = "Get file info", description = "Query file info by MD5")
    public ResponseEntity<FileUploadVO> getByMd5(
            @Parameter(description = "File MD5 hash") @RequestParam("md5") String md5) {
        FileUploadVO result = fileService.getByMd5(md5);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{md5}")
    @Operation(summary = "Delete file", description = "Delete file by MD5")
    public ResponseEntity<Void> delete(
            @Parameter(description = "File MD5 hash") @PathVariable("md5") String md5) {
        log.info("File delete request: md5={}", md5);
        fileService.delete(md5);
        return ResponseEntity.noContent().build();
    }
}
