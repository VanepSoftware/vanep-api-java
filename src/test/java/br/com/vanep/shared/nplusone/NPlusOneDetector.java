package br.com.vanep.shared.nplusone;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Suite-wide N+1 detector (VAN-81). Off by default; {@code make audit-n-plus-one-suite} turns it on
 * through two Hibernate settings, so the whole test suite becomes the data set.
 *
 * <p>Each Hibernate session (one service transaction, since open-in-view is off) records the
 * selects it prepares. When the session ends, a select whose SQL ran more than once — same text,
 * other parameters — is the N+1 signature, and is appended to {@code target/n-plus-one-report.txt}
 * with the production call site that issued it.
 */
public final class NPlusOneDetector implements StatementInspector {

  static final Path REPORT = Path.of("target", "n-plus-one-report.txt");

  static final ThreadLocal<Deque<SessionLog>> SESSIONS = ThreadLocal.withInitial(ArrayDeque::new);

  @Override
  public String inspect(String sql) {
    SessionLog session = SESSIONS.get().peek();
    if (session != null && sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) {
      session.record(sql);
    }
    return sql;
  }

  static final class SessionLog {
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, String> callSites = new LinkedHashMap<>();

    void record(String sql) {
      int count = counts.merge(sql, 1, Integer::sum);
      if (count == 2) {
        callSites.put(sql, callSite());
      }
    }

    void report() {
      String lines =
          counts.entrySet().stream()
              .filter(entry -> entry.getValue() > 1)
              .map(
                  entry ->
                      "%d\t%s\t%s"
                          .formatted(
                              entry.getValue(), callSites.get(entry.getKey()), entry.getKey()))
              .collect(Collectors.joining("\n"));
      if (!lines.isEmpty()) {
        append(lines + "\n");
      }
    }

    // First application frames (outside tests and proxies), plus the test that drove them.
    private static String callSite() {
      StackTraceElement[] stack = new Throwable().getStackTrace();
      String app =
          Arrays.stream(stack)
              .filter(frame -> frame.getClassName().startsWith("br.com.vanep."))
              .filter(frame -> !frame.getClassName().startsWith("br.com.vanep.shared.nplusone"))
              .filter(frame -> !frame.getClassName().contains("$$"))
              .filter(frame -> !frame.getClassName().endsWith("Test"))
              .limit(3)
              .map(frame -> simpleName(frame) + ":" + frame.getLineNumber())
              .collect(Collectors.joining(" < "));
      String test =
          Arrays.stream(stack)
              .filter(frame -> frame.getClassName().endsWith("Test"))
              .map(frame -> simpleName(frame) + "." + frame.getMethodName())
              .reduce((first, second) -> second)
              .orElse("?");
      return (app.isEmpty() ? "(no app frame)" : app) + "\t" + test;
    }

    private static String simpleName(StackTraceElement frame) {
      String name = frame.getClassName();
      return name.substring(name.lastIndexOf('.') + 1) + "." + frame.getMethodName();
    }
  }

  private static synchronized void append(String text) {
    try {
      Files.createDirectories(REPORT.getParent());
      Files.writeString(
          REPORT,
          text,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
