package com.sakana.file.util;

import java.util.Arrays;
import java.util.List;

public class FileUtil {

    private static final List<String> IMAGE_TYPES = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    );

    private static final List<String> VIDEO_TYPES = Arrays.asList(
            "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm"
    );

    private static final List<String> AUDIO_TYPES = Arrays.asList(
            "mp3", "wav", "ogg", "flac", "aac", "m4a"
    );

    private static final List<String> DOCUMENT_TYPES = Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf"
    );

    public static String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    public static String getFileType(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "other";
        }
        String ext = extension.toLowerCase();

        if (IMAGE_TYPES.contains(ext)) {
            return "image";
        } else if (VIDEO_TYPES.contains(ext)) {
            return "video";
        } else if (AUDIO_TYPES.contains(ext)) {
            return "audio";
        } else if (DOCUMENT_TYPES.contains(ext)) {
            return "document";
        }
        return "other";
    }
}
