package com.prlens.support;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 비밀값을 가린 뒤 BOM 없는 UTF-8로 쓰는 표준 스트림 (요구사항 19.4, 22.3). 줄 단위로 모았다가 쓰므로 쓰기 조각 경계에 걸친 비밀값도 가려진다.
 *
 * <p>줄이 끝나기 전에 {@link #flush()}나 {@link #checkError()}를 부르면 나머지를 내보내되, 끝이 비밀값의 앞부분과 같으면 그 부분은 줄이
 * 끝나거나 {@link #close()}할 때까지 남긴다. 그래서 flush를 사이에 두고 나뉘어 쓰인 비밀값도 가려진다.
 */
public final class MaskingPrintStream extends PrintStream {

  public MaskingPrintStream(OutputStream target, SecretMasker masker) {
    super(new LineMaskingOutputStream(target, masker), false, StandardCharsets.UTF_8);
  }

  /** OS 코드 페이지와 {@code file.encoding}에 의존하지 않도록 표준 출력을 직접 연다. */
  public static MaskingPrintStream stdout(SecretMasker masker) {
    return new MaskingPrintStream(new FileOutputStream(FileDescriptor.out), masker);
  }

  public static MaskingPrintStream stderr(SecretMasker masker) {
    return new MaskingPrintStream(new FileOutputStream(FileDescriptor.err), masker);
  }

  private static final class LineMaskingOutputStream extends OutputStream {

    private final OutputStream target;
    private final SecretMasker masker;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();

    LineMaskingOutputStream(OutputStream target, SecretMasker masker) {
      this.target = target;
      this.masker = masker;
    }

    @Override
    public synchronized void write(int b) throws IOException {
      line.write(b);
      if (b == '\n') {
        emit(true);
      }
    }

    @Override
    public synchronized void write(byte[] bytes, int off, int len) throws IOException {
      int start = off;
      for (int i = off; i < off + len; i++) {
        if (bytes[i] == '\n') {
          line.write(bytes, start, i + 1 - start);
          emit(true);
          start = i + 1;
        }
      }
      line.write(bytes, start, off + len - start);
    }

    @Override
    public synchronized void flush() throws IOException {
      emit(false);
    }

    @Override
    public synchronized void close() throws IOException {
      try {
        emit(true);
      } finally {
        target.close();
      }
    }

    /**
     * @param all false면 비밀값의 앞부분일 수 있는 끝부분과, 아직 다 오지 않은 멀티바이트 문자의 바이트를 버퍼에 남긴다
     */
    private void emit(boolean all) throws IOException {
      if (line.size() > 0) {
        byte[] bytes = line.toByteArray();
        int complete = all ? bytes.length : completeUtf8Length(bytes);
        String text = new String(bytes, 0, complete, StandardCharsets.UTF_8);
        int end = all ? text.length() : masker.safePrefixLength(text);
        line.reset();
        line.writeBytes(text.substring(end).getBytes(StandardCharsets.UTF_8));
        line.write(bytes, complete, bytes.length - complete);
        target.write(masker.mask(text.substring(0, end)).getBytes(StandardCharsets.UTF_8));
      }
      target.flush();
    }

    /** 끝에 걸린 미완성 UTF-8 시퀀스를 뺀 길이. 문자 한가운데서 디코딩하면 대체 문자가 되므로 나머지 바이트가 올 때까지 미룬다. */
    private static int completeUtf8Length(byte[] bytes) {
      for (int back = 1; back <= 3 && back <= bytes.length; back++) {
        int b = bytes[bytes.length - back] & 0xFF;
        if ((b & 0xC0) == 0x80) {
          continue;
        }
        int needed = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC0 ? 2 : 1;
        return needed > back ? bytes.length - back : bytes.length;
      }
      return bytes.length;
    }
  }
}
