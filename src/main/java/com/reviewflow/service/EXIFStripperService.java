package com.reviewflow.service;

import com.reviewflow.exception.AvatarUploadFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class EXIFStripperService {

    public byte[] strip(MultipartFile file, String outputFormat) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new AvatarUploadFailedException("Unable to decode avatar image", null);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean written = ImageIO.write(image, outputFormat, output);
            if (!written) {
                throw new AvatarUploadFailedException("Unable to encode avatar image format: " + outputFormat, null);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new AvatarUploadFailedException("Failed to strip avatar metadata", e);
        }
    }
}
