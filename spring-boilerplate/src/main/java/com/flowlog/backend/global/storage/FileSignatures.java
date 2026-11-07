package com.flowlog.backend.global.storage;

import java.util.Locale;
import java.util.Map;

/**
 * 파일 내용(시그니처)이 확장자와 맞는지 확인. (확장자만 바꾼 파일 - 실행 파일·HTML 을 .pdf 등으로 위장 - 거부)
 *
 * <ul>
 *   <li>pdf : {@code %PDF-} (앞 1024바이트 안 - 규격상 앞에 다른 바이트 허용)</li>
 *   <li>docx·xlsx·pptx·hwpx·zip : ZIP ({@code PK\3\4}, 빈 ZIP {@code PK\5\6})</li>
 *   <li>doc·xls·ppt·hwp : OLE 복합 문서 ({@code D0 CF 11 E0 A1 B1 1A E1})</li>
 *   <li>png·jpg·jpeg·gif·webp : 이미지 시그니처</li>
 *   <li>txt·csv : 텍스트 (앞부분에 NUL 바이트 없음)</li>
 * </ul>
 * 목록에 없는 확장자는 false (허용 목록과 함께 써서 새 확장자를 추가하면 여기도 규칙을 넣게).
 *
 * @author flowlog
 * @since 2026-11-07
 */
public final class FileSignatures {

  /** 확인에 필요한 앞부분 길이. */
  public static final int HEAD_BYTES = 4096;

  private static final int[] ZIP = {'P', 'K', 3, 4};
  private static final int[] ZIP_EMPTY = {'P', 'K', 5, 6};
  private static final int[] OLE = {0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1};

  private static final Map<String, String> KIND = Map.ofEntries(
      Map.entry("pdf", "pdf"),
      Map.entry("zip", "zip"), Map.entry("docx", "zip"), Map.entry("xlsx", "zip"),
      Map.entry("pptx", "zip"), Map.entry("hwpx", "zip"),
      Map.entry("doc", "ole"), Map.entry("xls", "ole"), Map.entry("ppt", "ole"),
      Map.entry("hwp", "ole"),
      Map.entry("png", "png"), Map.entry("jpg", "jpeg"), Map.entry("jpeg", "jpeg"),
      Map.entry("gif", "gif"), Map.entry("webp", "webp"),
      Map.entry("txt", "text"), Map.entry("csv", "text"));

  private FileSignatures() {
  }

  /**
   * 내용이 확장자와 맞는지.
   *
   * @param extension 확장자 (대소문자 무관)
   * @param head 파일 앞부분 ({@link #HEAD_BYTES} 정도)
   * @return 맞으면 true
   */
  public static boolean matches(String extension, byte[] head) {
    String kind = KIND.get(extension == null ? "" : extension.toLowerCase(Locale.ROOT));
    if (kind == null || head == null) {
      return false;
    }
    return switch (kind) {
      case "pdf" -> indexOf(head, new int[] {'%', 'P', 'D', 'F', '-'}, 1024) >= 0;
      case "zip" -> startsWith(head, 0, ZIP) || startsWith(head, 0, ZIP_EMPTY);
      case "ole" -> startsWith(head, 0, OLE);
      case "text" -> isText(head);
      default -> kind.equals(imageKind(head));
    };
  }

  /**
   * 이미지 형식 (시그니처).
   *
   * @param head 파일 앞부분
   * @return png / jpeg / gif / webp, 이미지가 아니면 null
   */
  public static String imageKind(byte[] head) {
    if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
      return "jpeg";
    }
    if (startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return "png";
    }
    if (startsWith(head, 0, 'G', 'I', 'F', '8')) {
      return "gif";
    }
    if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P')) {
      return "webp";
    }
    return null;
  }

  private static boolean isText(byte[] head) {
    for (byte b : head) {
      if (b == 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean startsWith(byte[] content, int offset, int... signature) {
    if (content.length < offset + signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((content[offset + i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }

  private static int indexOf(byte[] content, int[] signature, int limit) {
    int last = Math.min(limit, content.length - signature.length);
    for (int i = 0; i <= last; i++) {
      if (startsWith(content, i, signature)) {
        return i;
      }
    }
    return -1;
  }
}
