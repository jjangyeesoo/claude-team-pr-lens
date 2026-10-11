package com.prlens.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** `MaskingPrintStream` (design.md "비밀정보 마스킹", "이식성", 요구사항 19.4, 22.3). 비밀값은 가짜다. */
class MaskingPrintStreamTest {

  private static final String TOKEN = "ghp_token123";

  private final ByteArrayOutputStream target = new ByteArrayOutputStream();
  private final MaskingPrintStream stream =
      new MaskingPrintStream(target, new SecretMasker(List.of(TOKEN)));

  private String written() {
    return target.toString(UTF_8);
  }

  @Test
  void streamMasksPrintedText() {
    stream.print("token " + TOKEN + "\n다음 줄 " + TOKEN + "\n");

    assertThat(written()).isEqualTo("token ***\n다음 줄 ***\n");
  }

  @Test
  void streamMasksStackTrace() {
    new IllegalStateException("인증 실패: " + TOKEN).printStackTrace(stream);
    stream.flush();

    assertThat(written())
        .contains("IllegalStateException: 인증 실패: ***")
        .contains("streamMasksStackTrace")
        .doesNotContain(TOKEN);
  }

  @Test
  void streamMasksSecretSplitAcrossWrites() {
    stream.print("ghp_");
    stream.print("token123\n");

    assertThat(written()).isEqualTo("***\n");
  }

  @Test
  void streamWritesUtf8WithoutBom() {
    stream.print("한글 😀\n");

    assertThat(target.toByteArray()).isEqualTo("한글 😀\n".getBytes(UTF_8));
  }

  @Test
  void streamHoldsPartialLineUntilLineEnd() {
    stream.print("abc");

    assertThat(target.size()).isZero();

    stream.print("\n");

    assertThat(written()).isEqualTo("abc\n");
  }

  @Test
  void flushWritesPendingPartialLine() {
    stream.print("x " + TOKEN);
    stream.flush();

    assertThat(written()).isEqualTo("x ***");
  }

  @Test
  void closeWritesPendingPartialLine() {
    stream.print("y " + TOKEN);
    stream.close();

    assertThat(written()).isEqualTo("y ***");
  }

  @Test
  void flushHoldsBackTailThatMayStartSecret() {
    stream.print("id ghp_");
    stream.flush();

    assertThat(written()).isEqualTo("id ");

    stream.print("token123\n");

    assertThat(written()).isEqualTo("id ***\n");
  }

  @Test
  void checkErrorDoesNotLeakSecretSplitAcrossWrites() {
    stream.print("ghp_");
    stream.checkError();
    stream.print("token123\n");

    assertThat(written()).isEqualTo("***\n");
  }

  @Test
  void flushDoesNotCutThroughSecretThatAlreadyAppeared() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MaskingPrintStream overlapping =
        new MaskingPrintStream(out, new SecretMasker(List.of("xab", "abc")));

    overlapping.print("xab");
    overlapping.flush();

    assertThat(out.size()).isZero();

    overlapping.print("c\n");

    assertThat(out.toString(UTF_8)).isEqualTo("***\n");
  }

  @Test
  void closeWritesHeldTailThatNeverBecameSecret() {
    stream.print("ghp_");
    stream.flush();
    stream.close();

    assertThat(written()).isEqualTo("ghp_");
  }

  @Test
  void writeWithOffsetMasksOnlyGivenRange() {
    byte[] bytes = ("zza " + TOKEN + "\nb").getBytes(UTF_8);

    stream.write(bytes, 2, bytes.length - 2);
    stream.flush();

    assertThat(written()).isEqualTo("a ***\nb");
  }

  @Test
  void singleByteWritesAreMaskedByLine() {
    for (byte b : ("한 " + TOKEN + "\n").getBytes(UTF_8)) {
      stream.write(b);
    }

    assertThat(written()).isEqualTo("한 ***\n");
  }

  @Test
  void crlfLineIsMaskedAndKeepsItsLineEnding() {
    stream.print("a " + TOKEN + "\r\n");

    assertThat(written()).isEqualTo("a ***\r\n");
  }

  @Test
  void flushInsideMultibyteCharacterKeepsCharacterIntact() {
    byte[] bytes = "한\n".getBytes(UTF_8);

    stream.write(bytes, 0, 2);
    stream.flush();
    stream.write(bytes, 2, bytes.length - 2);

    assertThat(target.toByteArray()).isEqualTo(bytes);
  }

  @Test
  void flushInsideMultibyteCharacterStillMasksNonAsciiSecret() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MaskingPrintStream korean = new MaskingPrintStream(out, new SecretMasker(List.of("가나다라")));
    byte[] bytes = "x가나다라y\n".getBytes(UTF_8);

    korean.write(bytes, 0, 6);
    korean.flush();
    korean.write(bytes, 6, bytes.length - 6);

    assertThat(out.toString(UTF_8)).isEqualTo("x***y\n");
  }

  /** 닫혔는지 기록하고, 켜 두면 쓰기에 실패하는 대상 스트림. */
  private static final class FlakyTarget extends OutputStream {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean failing;
    private boolean closed;

    @Override
    public void write(int b) throws IOException {
      if (failing) {
        throw new IOException("쓰기 실패");
      }
      bytes.write(b);
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  @Test
  void closeClosesTarget() {
    FlakyTarget flaky = new FlakyTarget();

    new MaskingPrintStream(flaky, new SecretMasker(List.of(TOKEN))).close();

    assertThat(flaky.closed).isTrue();
  }

  @Test
  void closeClosesTargetEvenWhenLastWriteFails() {
    FlakyTarget flaky = new FlakyTarget();
    MaskingPrintStream failing = new MaskingPrintStream(flaky, new SecretMasker(List.of(TOKEN)));

    flaky.failing = true;
    failing.print("x");
    failing.close();

    assertThat(flaky.closed).isTrue();
  }

  @Test
  void failedLineIsDroppedAndLaterLinesAreStillMasked() {
    FlakyTarget flaky = new FlakyTarget();
    MaskingPrintStream recovering = new MaskingPrintStream(flaky, new SecretMasker(List.of(TOKEN)));

    flaky.failing = true;
    recovering.print("first " + TOKEN + "\n");

    assertThat(recovering.checkError()).isTrue();

    flaky.failing = false;
    recovering.print("second " + TOKEN + "\n");

    assertThat(flaky.bytes.toString(UTF_8)).isEqualTo("second ***\n");
  }
}
