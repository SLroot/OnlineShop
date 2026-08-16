package com.shop.online_shop.service;

import com.shop.online_shop.exception.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private static final long MAX_SIZE_BYTES = 2 * 1024 * 1024;   // 2MB

    private final Path uploadRoot;
    private final String publicPrefix;

    public FileStorageService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.public-prefix}") String publicPrefix) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix;
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(uploadRoot);
        log.info("Upload directory: {}", uploadRoot);
    }

    /** فایل را ذخیره و نام یکتای آن را برمی‌گرداند */
    public String store(MultipartFile file) {
        validate(file);

        String extension = extensionOf(file.getContentType());
        String fileName = UUID.randomUUID() + extension;

        try {
            Path target = uploadRoot.resolve(fileName).normalize();

            // محافظت در برابر path traversal
            if (!target.getParent().equals(uploadRoot)) {
                throw ApiException.badRequest("مسیر فایل نامعتبر است");
            }

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return fileName;

        } catch (IOException e) {
            log.error("File storage failed", e);
            throw ApiException.badRequest("ذخیره فایل با خطا مواجه شد");
        }
    }

    public void delete(String fileName) {
        try {
            Files.deleteIfExists(uploadRoot.resolve(fileName).normalize());
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", fileName, e.getMessage());
        }
    }

    public String publicUrl(String fileName) {
        return publicPrefix + "/" + fileName;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("فایلی ارسال نشده است");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw ApiException.badRequest("حجم فایل نباید بیش از ۲ مگابایت باشد");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("فقط تصویر JPEG، PNG یا WebP مجاز است");
        }
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default           -> ".jpg";
        };
    }
}