package com.example.studycopilot.service.attachment;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/** 根据实际内容识别头像格式，不采信客户端扩展名或 MIME。 */
public record AvatarImage(byte[] bytes, String mediaType, String extension) {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] PNG_END = {0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82};

    public static AvatarImage read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ScErrorCode.AVATAR_TYPE_INVALID);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ScErrorCode.AVATAR_TOO_LARGE);
        }
        byte[] content;
        try (var stream = file.getInputStream()) {
            content = stream.readNBytes(MAX_BYTES + 1);
        } catch (IOException ex) {
            throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessException(ScErrorCode.AVATAR_TOO_LARGE);
        }
        if (content.length >= 33 && Arrays.equals(PNG, Arrays.copyOf(content, 8))
                && ascii(content, 12, 4).equals("IHDR")
                && Arrays.equals(PNG_END, Arrays.copyOfRange(content, content.length - 12, content.length))
                && pngChunksValid(content) && imageDimensionsValid(content)) {
            return new AvatarImage(content, "image/png", "png");
        }
        if (content.length >= 12 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff && (content[content.length - 2] & 0xff) == 0xff
                && (content[content.length - 1] & 0xff) == 0xd9 && imageDimensionsValid(content)) {
            return new AvatarImage(content, "image/jpeg", "jpg");
        }
        if (content.length >= 20 && ascii(content, 0, 4).equals("RIFF") && ascii(content, 8, 4).equals("WEBP")
                && (ascii(content, 12, 4).equals("VP8 ") || ascii(content, 12, 4).equals("VP8L")
                    || ascii(content, 12, 4).equals("VP8X"))
                && littleEndian(content, 4) == content.length - 8L
                && littleEndian(content, 16) > 0 && littleEndian(content, 16) <= content.length - 20L
                && webpChunksValid(content)) {
            return new AvatarImage(content, "image/webp", "webp");
        }
        throw new BusinessException(ScErrorCode.AVATAR_TYPE_INVALID);
    }

    private static boolean imageDimensionsValid(byte[] content) {
        // Read metadata only: do not inflate an arbitrarily large raster from a small compressed upload.
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return reader.getWidth(0) > 0 && reader.getHeight(0) > 0;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean pngChunksValid(byte[] content) {
        int offset = 8;
        boolean hasData = false;
        while (offset <= content.length - 12) {
            long length = bigEndian(content, offset);
            if (length > content.length - offset - 12L) {
                return false;
            }
            int size = (int) length;
            String type = ascii(content, offset + 4, 4);
            if (offset == 8 && (!type.equals("IHDR") || size != 13)) {
                return false;
            }
            var crc = new CRC32();
            crc.update(content, offset + 4, size + 4);
            if (crc.getValue() != bigEndian(content, offset + 8 + size)) {
                return false;
            }
            hasData |= type.equals("IDAT") && size > 0;
            offset += size + 12;
            if (type.equals("IEND")) {
                return size == 0 && offset == content.length && hasData;
            }
        }
        return false;
    }

    private static boolean webpChunksValid(byte[] content) {
        int offset = 12;
        while (offset <= content.length - 8) {
            long length = littleEndian(content, offset + 4);
            long next = offset + 8L + length + (length & 1);
            if (next > content.length) {
                return false;
            }
            offset = (int) next;
        }
        return offset == content.length;
    }

    private static long bigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xffL) << 24) | ((bytes[offset + 1] & 0xffL) << 16)
                | ((bytes[offset + 2] & 0xffL) << 8) | (bytes[offset + 3] & 0xffL);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static long littleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }
}
