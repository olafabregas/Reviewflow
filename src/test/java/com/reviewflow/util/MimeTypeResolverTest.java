package com.reviewflow.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MimeTypeResolverTest {

  @Test
  void getMimeType_pdfExtension_returnsPdfMimeType() {
    String mimeType = MimeTypeResolver.getMimeType("document.pdf");
    assertEquals("application/pdf", mimeType);
  }

  @Test
  void getMimeType_jpgExtension_returnsJpegMimeType() {
    String mimeType = MimeTypeResolver.getMimeType("photo.jpg");
    assertEquals("image/jpeg", mimeType);
  }

  @Test
  void getMimeType_jpegExtension_returnsJpegMimeType() {
    String mimeType = MimeTypeResolver.getMimeType("photo.jpeg");
    assertEquals("image/jpeg", mimeType);
  }

  @Test
  void getMimeType_pngExtension_returnsPngMimeType() {
    String mimeType = MimeTypeResolver.getMimeType("image.png");
    assertEquals("image/png", mimeType);
  }

  @Test
  void getMimeType_webpExtension_returnsWebpMimeType() {
    String mimeType = MimeTypeResolver.getMimeType("image.webp");
    assertEquals("image/webp", mimeType);
  }

  @Test
  void getMimeType_unknownExtension_returnsNull() {
    String mimeType = MimeTypeResolver.getMimeType("archive.zip");
    assertNull(mimeType);
  }

  @Test
  void isPreviewable_pdfMimeType_returnsTrue() {
    assertTrue(MimeTypeResolver.isPreviewable("application/pdf"));
  }

  @Test
  void isPreviewable_imageMimeTypes_returnsTrue() {
    assertTrue(MimeTypeResolver.isPreviewable("image/jpeg"));
    assertTrue(MimeTypeResolver.isPreviewable("image/png"));
    assertTrue(MimeTypeResolver.isPreviewable("image/webp"));
  }

  @Test
  void isPreviewable_nonPreviewableMimeType_returnsFalse() {
    assertFalse(MimeTypeResolver.isPreviewable("application/zip"));
    assertFalse(MimeTypeResolver.isPreviewable("text/plain"));
    assertFalse(MimeTypeResolver.isPreviewable("application/vnd.openxmlformats"));
  }

  @Test
  void isPreviewable_nullMimeType_returnsFalse() {
    assertFalse(MimeTypeResolver.isPreviewable(null));
  }

  @Test
  void getMimeType_caseInsensitive_returnsMimeType() {
    // Extensions should be resolved case-insensitively
    String mimeType1 = MimeTypeResolver.getMimeType("document.PDF");
    String mimeType2 = MimeTypeResolver.getMimeType("document.pdf");
    assertEquals(mimeType1, mimeType2);
  }

  @Test
  void isPreviewable_caseInsensitive_handlesMimeTypes() {
    // MIME types comparison should be case-insensitive
    assertTrue(MimeTypeResolver.isPreviewable("APPLICATION/PDF"));
    assertTrue(MimeTypeResolver.isPreviewable("IMAGE/JPEG"));
  }
}
