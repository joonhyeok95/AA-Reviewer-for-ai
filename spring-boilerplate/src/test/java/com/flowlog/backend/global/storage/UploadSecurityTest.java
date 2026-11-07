package com.flowlog.backend.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 업로드 보안 유틸 단위 테스트 - 파일 시그니처, 이미지 정규화(메타데이터 제거·화소 제한).
 *
 * @author flowlog
 * @since 2026-11-07
 */
class UploadSecurityTest {

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("시그니처 - 확장자별 내용 일치 (PDF·ZIP 계열·OLE·텍스트·이미지), 모르는 확장자는 거부")
  void signatures() {
    // given / when / then
    assertThat(FileSignatures.matches("pdf", bytes("%PDF-1.7"))).isTrue();
    assertThat(FileSignatures.matches("PDF", bytes("\n\n%PDF-1.4"))).isTrue();
    assertThat(FileSignatures.matches("pdf", bytes("MZ..."))).isFalse();
    assertThat(FileSignatures.matches("docx", bytes("PK\u0003\u0004...."))).isTrue();
    assertThat(FileSignatures.matches("hwpx", bytes("PK\u0005\u0006"))).isTrue();
    assertThat(FileSignatures.matches("xlsx", bytes("<html>"))).isFalse();
    byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
        (byte) 0xE1, 0};
    assertThat(FileSignatures.matches("hwp", ole)).isTrue();
    assertThat(FileSignatures.matches("doc", bytes("PK\u0003\u0004"))).isFalse();
    assertThat(FileSignatures.matches("txt", "안내".getBytes(StandardCharsets.UTF_8))).isTrue();
    assertThat(FileSignatures.matches("csv", new byte[] {'a', 0, 'b'})).isFalse();
    assertThat(FileSignatures.matches("png", bytes("GIF89a"))).isFalse();
    assertThat(FileSignatures.matches("gif", bytes("GIF89a"))).isTrue();
    assertThat(FileSignatures.matches("exe", bytes("MZ"))).isFalse();
  }

  @Test
  @DisplayName("정규화 - 다시 그린 이미지에는 메타데이터가 없다 (정리 작업이 건너뛸 수 있게)")
  void normalizedHasNoMetadata() throws Exception {
    // given : 주석(COM) 이 붙은 JPEG
    byte[] jpeg = jpeg(10, 6);
    byte[] comment = bytes("secret location");
    ByteArrayOutputStream withComment = new ByteArrayOutputStream();
    withComment.write(jpeg, 0, 2);
    withComment.write(0xFF);
    withComment.write(0xFE);
    withComment.write((comment.length + 2) >> 8);
    withComment.write((comment.length + 2) & 0xFF);
    withComment.writeBytes(comment);
    withComment.write(jpeg, 2, jpeg.length - 2);
    byte[] original = withComment.toByteArray();
    assertThat(ImageNormalizer.hasMetadata(original)).isTrue();

    // when
    ImageNormalizer.Result result = ImageNormalizer.normalize(original, 1_000_000, 2560);

    // then
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(ImageNormalizer.hasMetadata(result.content())).isFalse();
    assertThat(new String(result.content(), StandardCharsets.ISO_8859_1)).doesNotContain("secret");
  }

  @Test
  @DisplayName("정규화 - 화소 초과·이미지 아님 거부, 긴 변 줄이기")
  void limits() throws Exception {
    // given
    byte[] big = jpeg(400, 200);

    // when / then
    assertThatThrownBy(() -> ImageNormalizer.normalize(big, 10_000, 2560))
        .isInstanceOfSatisfying(ImageNormalizer.Rejected.class,
            e -> assertThat(e.reason()).isEqualTo(ImageNormalizer.Rejected.Reason.TOO_MANY_PIXELS));
    assertThatThrownBy(() -> ImageNormalizer.normalize(bytes("<svg onload=alert(1)>"), 10_000, 2560))
        .isInstanceOfSatisfying(ImageNormalizer.Rejected.class,
            e -> assertThat(e.reason()).isEqualTo(ImageNormalizer.Rejected.Reason.UNREADABLE));
    ImageNormalizer.Result small = ImageNormalizer.normalize(big, 1_000_000, 100);
    assertThat(small.width()).isEqualTo(100);
    assertThat(small.height()).isEqualTo(50);
  }

  @Test
  @DisplayName("정규화 - 1x1 RGBA PNG 도 읽는다 (CRC 가 틀린 손상 파일은 거부)")
  void tinyPng() {
    // given
    byte[] png = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNwav3yHwAFgwK74sCXXQAAAABJRU5ErkJggg==");

    // when
    ImageNormalizer.Result result = ImageNormalizer.normalize(png, 1_000_000, 2560);

    // then
    assertThat(result.width()).isEqualTo(1);
  }

  private static byte[] jpeg(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpeg", out);
    return out.toByteArray();
  }
}
