package com.flowlog.backend.global.storage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * 업로드 이미지 정규화 - <b>다시 그려 저장</b>한다.
 *
 * <ul>
 *   <li>메타데이터 제거 : 휴대폰 사진의 EXIF(촬영 위치 GPS, 기기 정보), 주석 등은 새 파일에 남지 않는다.
 *       대신 EXIF 회전값(Orientation)은 픽셀에 반영해 세로 사진이 눕지 않게</li>
 *   <li>압축 폭탄 차단 : 픽셀을 풀기 전에 머리(헤더)의 가로·세로로 최대 화소를 확인</li>
 *   <li>긴 변이 maxSide 보다 크면 줄인다 (PDF·화면용으로 충분, 저장 용량·메모리 절약)</li>
 *   <li>결과 형식 : 투명도가 있으면 PNG, 아니면 JPEG (품질 0.9). 애니메이션 GIF 는 첫 장면</li>
 *   <li>이미지 안에 끼워 넣은 다른 내용(HTML·스크립트 폴리글랏)도 다시 그리면서 사라진다</li>
 * </ul>
 * WEBP·CMYK JPEG 읽기는 TwelveMonkeys ImageIO 플러그인.
 *
 * @author flowlog
 * @since 2026-11-07
 */
public final class ImageNormalizer {

  static {
    // 실행 jar(중첩 클래스로더)에서도 플러그인(WEBP 등)을 찾게
    ImageIO.scanForPlugins();
  }

  private ImageNormalizer() {
  }

  /**
   * 정규화.
   *
   * @param input 원본
   * @param maxPixels 최대 화소 (가로 × 세로)
   * @param maxSide 결과 긴 변 최대 (px)
   * @return 정규화한 이미지
   * @throws Rejected 이미지가 아니거나 읽을 수 없음 / 화소 초과
   */
  public static Result normalize(byte[] input, long maxPixels, int maxSide) {
    BufferedImage image = read(input, maxPixels);
    int orientation = "jpeg".equals(FileSignatures.imageKind(input)) ? exifOrientation(input) : 1;
    image = orient(image, orientation);
    image = shrink(image, maxSide);
    boolean alpha = image.getColorModel().hasAlpha();
    byte[] out = alpha ? write(toType(image, BufferedImage.TYPE_INT_ARGB), "png", null)
        : write(toType(image, BufferedImage.TYPE_INT_RGB), "jpeg", 0.9f);
    return new Result(out, alpha ? "image/png" : "image/jpeg", image.getWidth(), image.getHeight());
  }

  private static BufferedImage read(byte[] input, long maxPixels) {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
      if (readers == null || !readers.hasNext()) {
        throw new Rejected(Rejected.Reason.UNREADABLE);
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0) {
          throw new Rejected(Rejected.Reason.UNREADABLE);
        }
        if (pixels > maxPixels) {
          throw new Rejected(Rejected.Reason.TOO_MANY_PIXELS);
        }
        BufferedImage image = reader.read(0);
        if (image == null) {
          throw new Rejected(Rejected.Reason.UNREADABLE);
        }
        return image;
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof Rejected rejected) {
        throw rejected;
      }
      throw new Rejected(Rejected.Reason.UNREADABLE);
    }
  }

  /** EXIF 회전값 반영 (1 그대로, 3 180°, 6 시계 90°, 8 반시계 90°, 2·4·5·7 뒤집기 포함). */
  private static BufferedImage orient(BufferedImage image, int orientation) {
    if (orientation <= 1 || orientation > 8) {
      return image;
    }
    int w = image.getWidth();
    int h = image.getHeight();
    boolean swap = orientation >= 5;
    AffineTransform t = new AffineTransform();
    switch (orientation) {
      case 2 -> {
        t.translate(w, 0);
        t.scale(-1, 1);
      }
      case 3 -> {
        t.translate(w, h);
        t.rotate(Math.PI);
      }
      case 4 -> {
        t.translate(0, h);
        t.scale(1, -1);
      }
      case 5 -> {
        t.rotate(-Math.PI / 2);
        t.scale(-1, 1);
      }
      case 6 -> {
        t.translate(h, 0);
        t.rotate(Math.PI / 2);
      }
      case 7 -> {
        // (x, y) → (h - y, w - x)
        t.translate(h, w);
        t.rotate(-Math.PI / 2);
        t.scale(1, -1);
      }
      case 8 -> {
        t.translate(0, w);
        t.rotate(3 * Math.PI / 2);
      }
      default -> {
        return image;
      }
    }
    BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, typeOf(image));
    Graphics2D g = out.createGraphics();
    try {
      g.drawImage(image, t, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static BufferedImage shrink(BufferedImage image, int maxSide) {
    int w = image.getWidth();
    int h = image.getHeight();
    int longest = Math.max(w, h);
    if (longest <= maxSide) {
      return image;
    }
    double ratio = (double) maxSide / longest;
    int nw = Math.max(1, (int) Math.round(w * ratio));
    int nh = Math.max(1, (int) Math.round(h * ratio));
    BufferedImage out = new BufferedImage(nw, nh, typeOf(image));
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(image, 0, 0, nw, nh, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static int typeOf(BufferedImage image) {
    return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
  }

  private static BufferedImage toType(BufferedImage image, int type) {
    if (image.getType() == type) {
      return image;
    }
    BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), type);
    Graphics2D g = out.createGraphics();
    try {
      if (type == BufferedImage.TYPE_INT_RGB) {
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
      }
      g.drawImage(image, 0, 0, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static byte[] write(BufferedImage image, String format, Float quality) {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
    if (!writers.hasNext()) {
      throw new IllegalStateException("이미지 저장기 없음: " + format);
    }
    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (quality != null && param.canWriteCompressed()) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(image, null, null), param);
      out.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("이미지 저장 실패", e);
    } finally {
      writer.dispose();
    }
  }

  /**
   * 메타데이터(EXIF·XMP·주석·텍스트 청크)가 남아 있는지. 정규화한 파일에는 없으므로 이미 정리한 파일을 건너뛸 때 쓴다.
   * JPEG/PNG 외 형식(GIF·WEBP)은 정규화하면 JPEG/PNG 가 되므로 true.
   *
   * @param content 이미지
   * @return 정리가 필요하면 true
   */
  public static boolean hasMetadata(byte[] content) {
    String kind = FileSignatures.imageKind(content);
    if ("jpeg".equals(kind)) {
      int pos = 2;
      while (pos + 4 <= content.length && (content[pos] & 0xFF) == 0xFF) {
        int marker = content[pos + 1] & 0xFF;
        int length = u16(content, pos + 2, false);
        if (marker == 0xDA || length < 2) {
          return false;
        }
        if (marker >= 0xE1 && marker <= 0xEF || marker == 0xFE) {
          return true; // APP1(EXIF·XMP) ~ APP15, 주석
        }
        pos += 2 + length;
      }
      return false;
    }
    if ("png".equals(kind)) {
      int pos = 8;
      while (pos + 8 <= content.length) {
        long length = u32(content, pos, false);
        String type = new String(content, pos + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if ("eXIf".equals(type) || "tEXt".equals(type) || "iTXt".equals(type)
            || "zTXt".equals(type)) {
          return true;
        }
        if ("IEND".equals(type) || length < 0 || length > content.length) {
          return false;
        }
        pos += 12 + (int) length;
      }
      return false;
    }
    return kind != null;
  }

  /**
   * JPEG EXIF 회전값 (APP1 "Exif" → TIFF IFD0 의 0x0112). 없거나 읽을 수 없으면 1.
   *
   * @param jpeg JPEG 내용
   * @return 1 ~ 8
   */
  static int exifOrientation(byte[] jpeg) {
    int pos = 2;
    while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
      int marker = jpeg[pos + 1] & 0xFF;
      int length = u16(jpeg, pos + 2, false);
      if (marker == 0xDA || length < 2) {
        break; // 영상 데이터 시작
      }
      int start = pos + 4;
      if (marker == 0xE1 && start + 6 <= jpeg.length && jpeg[start] == 'E' && jpeg[start + 1] == 'x'
          && jpeg[start + 2] == 'i' && jpeg[start + 3] == 'f') {
        return orientationInTiff(jpeg, start + 6, Math.min(jpeg.length, pos + 2 + length));
      }
      pos += 2 + length;
    }
    return 1;
  }

  private static int orientationInTiff(byte[] b, int tiff, int end) {
    if (tiff + 8 > end) {
      return 1;
    }
    boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
    int ifd = tiff + (int) u32(b, tiff + 4, little);
    if (ifd + 2 > end || ifd < tiff) {
      return 1;
    }
    int entries = u16(b, ifd, little);
    for (int i = 0; i < entries; i++) {
      int entry = ifd + 2 + i * 12;
      if (entry + 12 > end) {
        return 1;
      }
      if (u16(b, entry, little) == 0x0112) {
        int value = u16(b, entry + 8, little);
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  private static int u16(byte[] b, int at, boolean little) {
    int a = b[at] & 0xFF;
    int c = b[at + 1] & 0xFF;
    return little ? a | c << 8 : a << 8 | c;
  }

  private static long u32(byte[] b, int at, boolean little) {
    long v = 0;
    for (int i = 0; i < 4; i++) {
      int shift = little ? 8 * i : 8 * (3 - i);
      v |= (long) (b[at + i] & 0xFF) << shift;
    }
    return v;
  }

  /**
   * 정규화 결과.
   *
   * @param content 내용
   * @param contentType image/jpeg 또는 image/png
   * @param width 가로
   * @param height 세로
   */
  public record Result(byte[] content, String contentType, int width, int height) {
  }

  /** 받을 수 없는 이미지. */
  public static final class Rejected extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 사유. */
    public enum Reason {
      /** 이미지가 아니거나 읽을 수 없음. */
      UNREADABLE,
      /** 화소 초과. */
      TOO_MANY_PIXELS
    }

    private final Reason reason;

    Rejected(Reason reason) {
      super(reason.name(), null, false, false);
      this.reason = reason;
    }

    /**
     * 사유.
     *
     * @return 사유
     */
    public Reason reason() {
      return reason;
    }
  }
}
