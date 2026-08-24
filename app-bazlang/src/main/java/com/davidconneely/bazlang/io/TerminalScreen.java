package com.davidconneely.bazlang.io;

import com.davidconneely.bazlang.BStr;
import com.davidconneely.bazlang.ReportCode;
import com.davidconneely.bazlang.ReportException;
import com.davidconneely.cell.CellAttributes;
import com.davidconneely.cell.CellBuffer;
import com.davidconneely.cell.CellBufferRenderer;
import com.davidconneely.cell.QuadrantMode;
import com.davidconneely.repl.TerminalEngine;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** Terminal-based Screen implementation with dynamic screen regions. */
public class TerminalScreen extends AbstractCellBufferedScreen {
  private boolean printingSystemPrompt = false;
  private int currentInputHeight = 1;

  private int currentReservedRows() {
    return inputVisible ? (3 + currentInputHeight) : 0;
  }

  private final TerminalEngine engine;
  private final CellBufferRenderer renderer = new CellBufferRenderer();

  // Input state
  private boolean inputVisible = false;

  private enum InputContext {
    REPL,
    INPUT_NUMERIC,
    INPUT_STRING
  }

  private InputContext currentInputMode = InputContext.REPL;
  private String statusText = "";
  private String prefillText = null; // Pre-fill for next readln()

  // Pending render: print() marks dirty; flush()/println()/cls() drive render()
  private boolean dirty = false;

  private static final long FRAME_RENDER_INTERVAL_MS = 20L;
  private static final long FLUSH_RENDER_INTERVAL_MS = 100L;
  private long lastRenderTimeMs = 0L;
  private boolean fastMode = false;

  // Resize flag: set by WINCH signal handler
  private final AtomicBoolean resizePending = new AtomicBoolean(false);

  // Break flag for Ctrl+C
  private final AtomicBoolean breakFlag = new AtomicBoolean(false);

  // Track whether close() has been called
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // State for decoupled inkey/uinkey timeout approximation
  private BStr lastKey = BStr.EMPTY;
  private long lastKeyTime = 0L;

  @Override
  public boolean isInteractive() {
    return true;
  }

  public TerminalScreen(TerminalEngine engine) {
    super(createInitialBuffer(engine));
    this.engine = engine;

    this.engine.setInputHeightListener(this::adjustLayoutForInputHeight);
    this.engine.onInterrupt(() -> breakFlag.set(true));
    this.engine.onResize(() -> resizePending.set(true));

    // Ensure terminal is restored on JVM shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));

    render();
  }

  private static CellBuffer createInitialBuffer(TerminalEngine engine) {
    final int rawCols = engine.getColumns();
    final int cols = rawCols > 0 ? rawCols : 80;
    final int rawRows = engine.getRows();
    final int rows = rawRows > 0 ? rawRows : 25;
    return new CellBuffer(rows, cols, QuadrantMode.INSTANCE);
  }

  public void adjustLayoutForInputHeight(int newInputHeight) {
    if (this.currentInputHeight != newInputHeight) {
      this.currentInputHeight = newInputHeight;
      resizeBufferIfNeeded();
      render();
      engine.forceRedrawFromCursor();
    }
  }

  private void clearBuffer() {
    cellBuffer.clear();
    cursorRow = 0;
    cursorCol = 0;
  }

  private void resizeBufferIfNeeded() {
    final int rawCols = engine.getColumns();
    final int newCols = rawCols > 0 ? rawCols : 80;
    final int rawRows = engine.getRows();
    final int newRows = Math.max(1, (rawRows > 0 ? rawRows : 25) - currentReservedRows());
    if (newRows != cellBuffer.rows() || newCols != cellBuffer.cols()) {
      cellBuffer.resize(newRows, newCols);
      cursorRow = Math.min(cursorRow, cellBuffer.rows() - 1);
      cursorCol = Math.min(cursorCol, cellBuffer.cols() - 1);
    }
  }

  private void render() {
    lastRenderTimeMs = System.currentTimeMillis();
    dirty = false;
    resizePending.set(false);
    resizeBufferIfNeeded();
    final int termWidth = engine.getColumns();
    final int termHeight = engine.getRows();
    final int rowsToRender = Math.min(cellBuffer.rows(), termHeight);
    final int colsToRender = Math.min(cellBuffer.cols(), termWidth);

    final var out = engine.writer();
    final var frame = new StringBuilder(4096);
    // ?2026h: synchronized output start, ?25l: hide cursor, ?7l: disable auto-wrap
    frame.append("\033[?2026h\033[?25l\033[?7l");
    renderer.renderContentRows(frame, cellBuffer, rowsToRender, colsToRender);
    renderInputRows(frame, rowsToRender, termWidth);
    // ?2026l: synchronized output end, ?7h: enable auto-wrap
    frame.append("\033[?7h\033[?2026l");
    out.print(frame.toString());
    out.flush();
  }

  private void renderInputRows(StringBuilder out, int rowsToRender, int termWidth) {
    if (inputVisible) {
      out.append(String.format("\033[%d;1H", rowsToRender + 1))
          .append("\033[90m")
          .append("─".repeat(Math.max(0, termWidth)))
          .append("\033[m\033[K");

      for (int i = 0; i < currentInputHeight; i++) {
        out.append(String.format("\033[%d;1H\033[K", rowsToRender + 2 + i));
      }

      out.append(String.format("\033[%d;1H", rowsToRender + 2 + currentInputHeight))
          .append("\033[90m")
          .append("─".repeat(Math.max(0, termWidth)))
          .append("\033[m\033[K");

      final String lineText = getStatusLine(termWidth);
      out.append(String.format("\033[%d;1H", rowsToRender + 3 + currentInputHeight))
          .append("\033[37m")
          .append(lineText)
          .append("\033[m\033[K")
          .append(String.format("\033[%d;1H", rowsToRender + 2));
    }
  }

  private String getStatusLine(int termWidth) {
    final String rightText = "BazLang REPL";
    final int rightLen = rightText.length();
    final int spacesNeeded = termWidth - statusText.length() - rightLen;
    String lineText;
    if (spacesNeeded > 0) {
      lineText = statusText + " ".repeat(spacesNeeded) + rightText;
    } else {
      final int availableForStatus = termWidth - rightLen - 1;
      lineText =
          availableForStatus > 0
              ? statusText.substring(0, availableForStatus) + " " + rightText
              : statusText;
    }
    return lineText;
  }

  private void renderIfDue(boolean bypassFastMode) {
    if (fastMode && !bypassFastMode) {
      return;
    }
    if ((dirty || resizePending.get())
        && System.currentTimeMillis() - lastRenderTimeMs >= FRAME_RENDER_INTERVAL_MS) {
      render();
    }
  }

  private void renderIfDue() {
    renderIfDue(false);
  }

  @Override
  public void setFastMode(boolean fast) {
    this.fastMode = fast;
    if (!fast && dirty) {
      render();
    }
  }

  @Override
  public void cls() {
    clearBuffer();
    dirty = true;
    if (!fastMode) {
      render();
    }
  }

  @Override
  public void print(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }

    text.codePoints()
        .forEach(
            cp -> {
              if (cp == 10) { // Newline (LF)
                cursorRow++;
                cursorCol = 0;
                if (cursorRow >= cellBuffer.rows()) {
                  scrollBuffer();
                  cursorRow = cellBuffer.rows() - 1;
                }
              } else if (cp == 13) { // Carriage return (CR)
                cursorCol = 0;
              } else if (cp >= 32 && cp != 127) {
                if (cursorCol >= cellBuffer.cols()) {
                  cursorRow++;
                  cursorCol = 0;
                  if (cursorRow >= cellBuffer.rows()) {
                    scrollBuffer();
                    cursorRow = cellBuffer.rows() - 1;
                  }
                }
                if (cursorRow < cellBuffer.rows()) {
                  final int ink = activeInverse == 1 ? activePaper : activeInk;
                  final int paper = activeInverse == 1 ? activeInk : activePaper;
                  int cellFg = getMappedColour(ink, paper);
                  int cellBg = getMappedColour(paper, ink);
                  final int currentStyle = cellBuffer.getStyle(cursorRow, cursorCol);
                  final int cellStyle = getMappedStyle(currentStyle);
                  if (printingSystemPrompt) {
                    cellFg = CellAttributes.COLOUR_TYPE_INDEX | 4; // ANSI Blue
                    cellBg = CellAttributes.COLOUR_DEFAULT; // Default terminal background
                  }
                  cellBuffer.setCell(cursorRow, cursorCol, cp, cellFg, cellBg, cellStyle);
                  cursorCol++;
                }
              }
            });
    dirty = true;
  }

  @Override
  public void flush() {
    if ((dirty || resizePending.get())
        && System.currentTimeMillis() - lastRenderTimeMs >= FLUSH_RENDER_INTERVAL_MS) {
      render();
    }
  }

  @Override
  public void forceFlush() {
    if (dirty) {
      render();
    }
  }

  @Override
  public void println(String text) {
    print(text);
    println();
  }

  @Override
  public void println() {
    cursorRow++;
    cursorCol = 0;
    if (cursorRow >= cellBuffer.rows()) {
      scrollBuffer();
      cursorRow = cellBuffer.rows() - 1;
    }
    dirty = true;
    renderIfDue();
  }

  private void scrollBuffer() {
    cellBuffer.scrollUp();
  }

  @Override
  public void scroll() {
    cellBuffer.scrollUp();
    if (cursorRow > 0) {
      cursorRow--;
    }
    dirty = true;
    renderIfDue();
  }

  @Override
  protected void afterPlot() {
    dirty = true;
    renderIfDue();
  }

  @Override
  public String readln(InputMode mode) {
    currentInputMode =
        switch (mode) {
          case INPUT_NUMERIC -> InputContext.INPUT_NUMERIC;
          case INPUT_STRING -> InputContext.INPUT_STRING;
        };
    if (statusText.isEmpty()) {
      statusText =
          switch (mode) {
            case INPUT_NUMERIC -> "Please enter a number or expression";
            case INPUT_STRING -> "Please enter a text value";
          };
    }
    return readln("");
  }

  @Override
  public String readReplInput() {
    currentInputMode = InputContext.REPL;
    if (statusText.isEmpty()) {
      statusText = new ReportException(ReportCode.OK, 0, 1, "Ready").format();
    }
    return readln("");
  }

  @Override
  public String readln(String prompt) {
    if (!inputVisible) {
      inputVisible = true;
      currentInputHeight = 1;
      final int newRows = Math.max(1, engine.getRows() - currentReservedRows());
      if (cursorRow >= newRows) {
        final int scrollAmount = cursorRow - newRows + 1;
        for (int i = 0; i < scrollAmount; i++) {
          scrollBuffer();
        }
        cursorRow = newRows - 1;
      }
      resizeBufferIfNeeded();
    }
    if (prompt != null && !prompt.isEmpty()) {
      statusText = prompt.trim();
    }

    final String promptStr =
        switch (currentInputMode) {
          case REPL -> "\033[34m❯ \033[m";
          case INPUT_NUMERIC -> "\033[1m# \033[m";
          case INPUT_STRING -> "\033[1m$ \033[m";
        };

    render();
    engine.writer().print("\033[?25h");
    engine.writer().flush();

    engine.onResize(null);
    try {
      String fill = prefillText;
      prefillText = null;
      return engine.readLine(promptStr, fill);
    } finally {
      engine.onResize(() -> resizePending.set(true));
      engine.writer().print("\033[?25l");
      engine.writer().flush();
      inputVisible = false;
      statusText = "";
      currentInputHeight = 1;
      render();
    }
  }

  @Override
  public void prefillInput(String text) {
    prefillText = text;
  }

  @Override
  public boolean pollForBreak() {
    return breakFlag.compareAndSet(true, false);
  }

  private BStr readKeySequence() throws IOException {
    final BStr seq = KeyDecoder.decodeSequence(() -> engine.readKey(1L));
    if (seq != null && seq.isEmpty()) {
      breakFlag.set(true);
    }
    return seq;
  }

  @Override
  public BStr inkey() {
    renderIfDue(true);
    try {
      BStr seq = readKeySequence();
      if (seq != null) {
        while (true) {
          BStr next = readKeySequence();
          if (next == null) {
            break;
          }
          seq = next;
        }
        if (seq.isEmpty()) {
          return BStr.EMPTY;
        }
        lastKey = BStr.fromByte(seq.byteAt(0));
        lastKeyTime = System.currentTimeMillis();
        return lastKey;
      }
    } catch (IOException e) {
      // Ignore
    }
    if (System.currentTimeMillis() - lastKeyTime < 100L && lastKey.length() == 1) {
      return lastKey;
    }
    return BStr.EMPTY;
  }

  @Override
  public BStr uinkey() {
    renderIfDue(true);
    try {
      BStr seq = readKeySequence();
      if (seq != null) {
        while (true) {
          BStr next = readKeySequence();
          if (next == null) {
            break;
          }
          seq = next;
        }
        lastKey = seq;
        lastKeyTime = System.currentTimeMillis();
        return lastKey;
      }
    } catch (IOException e) {
      // Ignore
    }
    if (System.currentTimeMillis() - lastKeyTime < 100L) {
      return lastKey;
    }
    return BStr.EMPTY;
  }

  @Override
  public void waitForKey() {
    if (!inputVisible) {
      inputVisible = true;
      currentInputHeight = 1;
      int newRows = Math.max(1, engine.getRows() - currentReservedRows());
      if (cursorRow >= newRows) {
        final int scrollAmount = cursorRow - newRows + 1;
        for (int i = 0; i < scrollAmount; i++) {
          scrollBuffer();
        }
        cursorRow = newRows - 1;
      }
      resizeBufferIfNeeded();
    }
    forceFlush();
    final var out = engine.writer();
    out.printf("\033[%d;1H", cellBuffer.rows() + 3 + currentInputHeight);
    out.print("\033[37mPress any key to exit.\033[m");
    out.print("\033[K");
    out.flush();
    try {
      while (engine.readKey(100L) < 0) {
        if (breakFlag.get()) {
          break;
        }
      }
    } catch (IOException e) {
      // Ignore
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    stopBeep();
    beepLock.lock();
    try {
      if (beepLine != null) {
        beepLine.close();
        beepLine = null;
      }
    } finally {
      beepLock.unlock();
    }
    stopPlay();
    playLock.lock();
    try {
      if (playLine != null) {
        playLine.close();
        playLine = null;
      }
    } finally {
      playLock.unlock();
    }
    engine.close();
  }

  @Override
  public void setStatus(String status) {
    this.statusText = status;
  }

  @Override
  public void systemPrintln(String text) {
    printingSystemPrompt = true;
    try {
      println(text);
    } finally {
      printingSystemPrompt = false;
    }
  }

  // ===== BEEP (VirtualSpeaker) =====

  private static final float BEEP_SAMPLE_RATE = 44_100f;
  private static final int BEEP_CHUNK_SAMPLES = 4_410; // ~100ms/chunk: how often stopBeep() polls
  private static final short BEEP_AMPLITUDE = (short) (Short.MAX_VALUE * 0.3);
  // Explicit buffer size for both persistent lines, rather than whatever line.open(format) would
  // pick. Deliberately modest: since nothing ever queues idle silence (see playFrame's contract),
  // this bounds how much *real* audio can sit queued ahead of the speaker, and therefore the worst
  // case latency between triggering a sound and hearing it -- which matters for game feedback like
  // a paddle hit. Large enough to absorb ordinary OS scheduling jitter (comfortably above
  // Windows' ~15.6ms default scheduling quantum) without underrunning mid-note.
  private static final int LINE_BUFFER_BYTES = (int) (BEEP_SAMPLE_RATE * 0.08f) * 2; // ~80ms, mono

  // Opened lazily on the first BEEP and then kept open for the rest of the session (closed in
  // close()), rather than opened/closed fresh per call. Opening a line has real device/driver
  // startup latency - commonly tens of milliseconds - which is negligible against a 300ms+ jingle
  // note but can dominate (or exceed) a short ~50ms feedback blip's *nominal* duration, making it
  // barely audible. Reuse removes that latency from every call after the first, and also closes
  // the gap between chained notes in a jingle. Guarded by beepLock since beep() calls run on their
  // own short-lived thread (see beep()) and could otherwise race to open it concurrently - in
  // practice this can't happen from normal BASIC execution (StatementExecutor blocks for one
  // BEEP's duration before the interpreter can issue another), but the lock costs nothing and
  // keeps the field access correct regardless.
  private final ReentrantLock beepLock = new ReentrantLock();
  private SourceDataLine beepLine;
  private volatile AtomicBoolean beepPlaying;

  /** Converts a BEEP pitch (semitones above/below middle C) to Hz. Package-visible for testing. */
  static double frequencyForPitch(double pitch) {
    return Pitch.hzFromSemitonesAboveMiddleC(pitch);
  }

  @Override
  public void beep(double durationSeconds, double pitch) {
    if (durationSeconds <= 0) {
      return;
    }
    stopBeep();
    final double frequency = frequencyForPitch(pitch);
    final long totalSamples = Math.round(BEEP_SAMPLE_RATE * durationSeconds);
    final AtomicBoolean playing = new AtomicBoolean(true);
    beepPlaying = playing;
    final Thread player = new Thread(() -> playBeep(frequency, totalSamples, playing));
    player.setDaemon(true);
    player.setName("bazlang-beep");
    player.start();
  }

  @Override
  public void stopBeep() {
    final AtomicBoolean playing = beepPlaying;
    if (playing != null) {
      playing.set(false);
    }
    final SourceDataLine line;
    beepLock.lock();
    try {
      line = beepLine;
    } finally {
      beepLock.unlock();
    }
    if (line != null) {
      try {
        // flush(), not stop(): the line stays started for the rest of the session (see the field
        // doc above), so this only discards whatever's still queued from the interrupted tone -
        // it doesn't pause the line itself, which would otherwise need restarting before the next
        // beep() could write anything audible.
        line.flush();
      } catch (IllegalStateException e) {
        // Not open, or closing concurrently - fine, that's the goal anyway.
      }
    }
  }

  /** Returns the persistent playback line, opening (and starting) it on first use. */
  private SourceDataLine ensureBeepLine() throws LineUnavailableException {
    beepLock.lock();
    try {
      if (beepLine == null) {
        final AudioFormat format = new AudioFormat(BEEP_SAMPLE_RATE, 16, 1, true, false);
        final SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, LINE_BUFFER_BYTES);
        line.start();
        beepLine = line;
      }
      return beepLine;
    } finally {
      beepLock.unlock();
    }
  }

  // Runs on its own daemon thread (see beep()) so the interpreter thread stays free to run its
  // own PAUSE-style chunked wait/BREAK-poll loop (StatementExecutor.executeBeepStmt) rather than
  // blocking inside SourceDataLine.write()/drain() for the tone's whole duration.
  private void playBeep(double frequencyHz, long totalSamples, AtomicBoolean playing) {
    try {
      final SourceDataLine line = ensureBeepLine();
      final byte[] chunk = new byte[BEEP_CHUNK_SAMPLES * 2];
      long samplesWritten = 0;
      while (samplesWritten < totalSamples && playing.get()) {
        final int chunkSamples = (int) Math.min(BEEP_CHUNK_SAMPLES, totalSamples - samplesWritten);
        fillSquareWave(chunk, chunkSamples, frequencyHz, samplesWritten);
        line.write(chunk, 0, chunkSamples * 2);
        samplesWritten += chunkSamples;
      }
      if (playing.get()) {
        line.drain();
      }
    } catch (LineUnavailableException | IllegalStateException e) {
      // No audio device available (e.g. a headless/SSH session) - BEEP is silent, not fatal.
    }
  }

  /** Fills {@code buffer} with {@code sampleCount} 16-bit signed LE mono square-wave samples. */
  private static void fillSquareWave(
      byte[] buffer, int sampleCount, double frequencyHz, long startSampleIndex) {
    final double samplesPerCycle = BEEP_SAMPLE_RATE / frequencyHz;
    for (int i = 0; i < sampleCount; i++) {
      final boolean highHalf = (startSampleIndex + i) % samplesPerCycle < samplesPerCycle / 2;
      final short sample = highHalf ? BEEP_AMPLITUDE : (short) -BEEP_AMPLITUDE;
      buffer[i * 2] = (byte) (sample & 0xFF);
      buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
  }

  // ===== PLAY / APLAY (VirtualSpeaker) =====

  // Scaled down from BEEP_AMPLITUDE since up to 3 voices (each possibly tone+noise combined) are
  // summed into one buffer before writing; final samples are clamped defensively regardless.
  private static final short PLAY_TONE_AMPLITUDE = (short) (Short.MAX_VALUE * 0.2);
  // The DSL has no command that sets the shared noise generator's own pitch (unlike a channel's
  // tone, which comes from the note being played) -- no source consulted specifies one either, so
  // this toggle rate is a reasonable, documented default rather than a confirmed hardware value.
  private static final int PLAY_NOISE_TOGGLE_SAMPLES = 8;

  // Entirely independent lock/line from BEEP's own (see ensureBeepLine()) so a BEEP sound effect
  // can still sound while PLAY/APLAY music is playing, matching real hardware's independent
  // beeper/AY circuits -- see VirtualSpeaker's class doc.
  //
  // There is deliberately no render thread here: playFrame() synthesises exactly the audio it was
  // asked for and writes it straight to the line, on whichever thread called it (the interpreter
  // thread for blocking PLAY, the background APLAY thread otherwise). An earlier design used a
  // persistent render loop continuously writing whatever voices were last pushed to it -- which,
  // because a SourceDataLine is a FIFO that only blocks once full, meant it kept a whole buffer of
  // *silence* permanently queued ahead of the speaker, so every newly-triggered note landed behind
  // that backlog and played a full buffer-depth late (and short notes were easily clipped by the
  // fixed sampling granularity such a loop needs). See VirtualSpeaker.playFrame's contract.
  private final ReentrantLock playLock = new ReentrantLock();
  private SourceDataLine playLine;
  // Sample position and noise-LFSR state carried across successive playFrame() calls so a note
  // spanning several calls stays phase-continuous instead of clicking at every boundary. Guarded
  // by playLock, which the whole of playFrame() holds -- PLAY and APLAY never render concurrently
  // by design (a blocking PLAY stops any background APLAY first), but the lock keeps that a
  // correctness property of this class rather than an assumption about its callers.
  private final PlayRenderState playRenderState = new PlayRenderState();

  /** Mutable render-position state; see {@link #playRenderState}. */
  private static final class PlayRenderState {
    private long sampleIndex;
    private long noiseLfsr = 0x1FFFF;
  }

  @Override
  public void playFrame(VoiceFrame a, VoiceFrame b, VoiceFrame c, double durationSeconds) {
    if (durationSeconds <= 0) {
      return;
    }
    final int totalSamples = (int) Math.round(BEEP_SAMPLE_RATE * durationSeconds);
    if (totalSamples <= 0) {
      return;
    }
    playLock.lock();
    try {
      final SourceDataLine line = ensurePlayLine();
      // No-op when already running; resumes the line after drainPlay() parked it between sounds.
      line.start();
      final byte[] buffer = new byte[totalSamples * 2];
      playRenderState.noiseLfsr =
          fillPlayMix(
              buffer,
              totalSamples,
              a,
              b,
              c,
              playRenderState.sampleIndex,
              playRenderState.noiseLfsr);
      playRenderState.sampleIndex += totalSamples;
      line.write(buffer, 0, buffer.length);
    } catch (LineUnavailableException | IllegalStateException e) {
      // No audio device available (e.g. a headless/SSH session) - PLAY/APLAY is silent, not fatal.
    } finally {
      playLock.unlock();
    }
  }

  @Override
  public void stopPlay() {
    final SourceDataLine line;
    playLock.lock();
    try {
      line = playLine;
    } finally {
      playLock.unlock();
    }
    if (line != null) {
      try {
        // Discards audio already queued but not yet heard, so a note cut short (BREAK, or a fresh
        // PLAY/APLAY replacing it) stops promptly rather than playing out the rest of the buffer.
        line.stop();
        line.flush();
      } catch (IllegalStateException e) {
        // Not open, or closing concurrently - fine, that's the goal anyway.
      }
    }
  }

  @Override
  public void drainPlay() {
    final SourceDataLine line;
    playLock.lock();
    try {
      line = playLine;
    } finally {
      playLock.unlock();
    }
    if (line != null) {
      try {
        line.drain(); // let the tail of the last note actually play before parking the line
        line.stop();
        line.flush(); // leave no stale samples a starved line could otherwise replay
      } catch (IllegalStateException e) {
        // Not open, or closing concurrently - fine, that's the goal anyway.
      }
    }
  }

  /** Returns the persistent playback line, opening (and starting) it on first use. */
  private SourceDataLine ensurePlayLine() throws LineUnavailableException {
    playLock.lock();
    try {
      if (playLine == null) {
        final AudioFormat format = new AudioFormat(BEEP_SAMPLE_RATE, 16, 1, true, false);
        final SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, LINE_BUFFER_BYTES);
        line.start();
        playLine = line;
      }
      return playLine;
    } finally {
      playLock.unlock();
    }
  }

  /** Mixes up to 3 voices (tone and/or shared noise) into {@code buffer}. */
  private static long fillPlayMix(
      byte[] buffer,
      int sampleCount,
      VoiceFrame a,
      VoiceFrame b,
      VoiceFrame c,
      long startSampleIndex,
      long lfsrIn) {
    long lfsr = lfsrIn;
    for (int i = 0; i < sampleCount; i++) {
      if ((startSampleIndex + i) % PLAY_NOISE_TOGGLE_SAMPLES == 0) {
        final long bit = (lfsr ^ (lfsr >> 3)) & 1;
        lfsr = (lfsr >> 1) | (bit << 16);
      }
      final boolean noiseHigh = (lfsr & 1) != 0;
      final double mixed =
          voiceSample(a, startSampleIndex + i, noiseHigh)
              + voiceSample(b, startSampleIndex + i, noiseHigh)
              + voiceSample(c, startSampleIndex + i, noiseHigh);
      final short sample =
          (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixed)));
      buffer[i * 2] = (byte) (sample & 0xFF);
      buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    return lfsr;
  }

  private static double voiceSample(VoiceFrame voice, long sampleIndex, boolean noiseHigh) {
    if (voice.amplitude() <= 0 || (!voice.toneOn() && !voice.noiseOn())) {
      return 0;
    }
    double value = 0;
    if (voice.toneOn() && voice.frequencyHz() > 0) {
      final double samplesPerCycle = BEEP_SAMPLE_RATE / voice.frequencyHz();
      final boolean highHalf = sampleIndex % samplesPerCycle < samplesPerCycle / 2;
      value += highHalf ? 1.0 : -1.0;
    }
    if (voice.noiseOn()) {
      value += noiseHigh ? 1.0 : -1.0;
    }
    return value * voice.amplitude() * PLAY_TONE_AMPLITUDE;
  }
}
