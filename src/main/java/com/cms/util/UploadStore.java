package com.cms.util;

import jakarta.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores uploaded files OUTSIDE the deployed web app, so they survive redeploys and
 * can only be downloaded through a servlet that checks access.
 *
 * Location: system property "cms.upload.dir", else environment variable
 * CMS_UPLOAD_DIR, else "cms-uploads" under Tomcat's base folder (catalina.base).
 * Only PDF, PNG and JPEG files are accepted, verified by their first bytes.
 */
public final class UploadStore {

    public static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB (matches web.xml multipart-config)

    private UploadStore() {
    }

    /** Thrown with a user-facing message when a file is rejected. */
    public static final class RejectedFileException extends Exception {
        private static final long serialVersionUID = 1L;

        public RejectedFileException(String message) {
            super(message);
        }
    }

    public static Path baseDir() {
        String dir = System.getProperty("cms.upload.dir");
        if (dir == null || dir.isEmpty())
            dir = System.getenv("CMS_UPLOAD_DIR");
        if (dir == null || dir.isEmpty()) {
            String base = System.getProperty("catalina.base", System.getProperty("java.io.tmpdir"));
            dir = base + File.separator + "cms-uploads";
        }
        return Path.of(dir).toAbsolutePath().normalize();
    }

    /**
     * Saves an uploaded part under the given sub-folder with a random name.
     * Returns the stored relative path (e.g. "challans/3f2a....pdf"), or null when
     * no file was chosen.
     */
    public static String save(Part part, String subFolder) throws IOException, RejectedFileException {
        if (part == null || part.getSize() == 0 || part.getSubmittedFileName() == null
                || part.getSubmittedFileName().isEmpty())
            return null;
        long max = DemoMode.maxUploadBytes();
        if (part.getSize() > max)
            throw new RejectedFileException("The attachment is larger than " + (max / (1024 * 1024)) + " MB"
                    + (DemoMode.isOn() ? " (the limit in this demo)." : "."));

        byte[] head = new byte[8];
        int n;
        try (InputStream in = part.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        }
        String ext = detectExtension(head, n);
        if (ext == null)
            throw new RejectedFileException("Only PDF, PNG or JPG attachments are allowed.");

        Path folder = baseDir().resolve(subFolder).normalize();
        Files.createDirectories(folder);
        String name = UUID.randomUUID() + "." + ext; // never trust the client's file name
        try (InputStream in = part.getInputStream()) {
            Files.copy(in, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        return subFolder + "/" + name;
    }

    /** The file for a stored relative path, or null if missing or outside the upload folder. */
    public static File resolve(String relativePath) {
        if (relativePath == null || relativePath.isEmpty())
            return null;
        Path base = baseDir();
        Path p = base.resolve(relativePath).normalize();
        if (!p.startsWith(base))
            return null; // path traversal attempt
        File f = p.toFile();
        return f.isFile() ? f : null;
    }

    public static boolean delete(String relativePath) {
        File f = resolve(relativePath);
        return f != null && f.delete();
    }

    public static String contentType(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".png"))
            return "image/png";
        return "image/jpeg";
    }

    // PDF "%PDF", PNG "\x89PNG", JPEG "\xFF\xD8\xFF"
    private static String detectExtension(byte[] b, int n) {
        if (n >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F')
            return "pdf";
        if (n >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
            return "png";
        if (n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF)
            return "jpg";
        return null;
    }
}
