package com.sakana.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class Md5Service {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    public String calculateMd5(MultipartFile file) {
        try {
            return calculateMd5(file.getInputStream());
        } catch (IOException e) {
            log.error("Failed to calculate file MD5", e);
            throw new RuntimeException("Failed to calculate file MD5", e);
        }
    }

    public String calculateMd5(InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to calculate MD5", e);
            throw new RuntimeException("Failed to calculate MD5", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
}
