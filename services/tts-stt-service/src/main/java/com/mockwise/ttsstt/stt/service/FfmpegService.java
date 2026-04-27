package com.mockwise.ttsstt.stt.service;

import com.mockwise.ttsstt.common.config.TtsSttProperties;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FfmpegService {

    TtsSttProperties props;

    @PostConstruct
    void ensureTmpDir() throws IOException {
        Path tmp = Paths.get(props.ffmpeg().tmpDir());
        if (!Files.exists(tmp)) {
            Files.createDirectories(tmp);
            log.info("Created ffmpeg tmp dir at {}", tmp);
        }
    }

    /**
     * Extract audio track from a video file at {@code inputPath} into a 16kHz mono opus
     * file. Returns the path to the produced audio file. Caller is responsible for
     * deletion (typically in a finally block).
     */
    public Path extractAudio(Path inputPath) {
        Path output = Paths.get(props.ffmpeg().tmpDir(), UUID.randomUUID() + ".opus");
        List<String> cmd = List.of(
                props.ffmpeg().bin(),
                "-y", "-loglevel", "error",
                "-i", inputPath.toString(),
                "-vn",
                "-acodec", "libopus",
                "-b:a", "32k",
                "-ar", "16000",
                "-ac", "1",
                output.toString());

        log.debug("ffmpeg extract: {}", cmd);
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = p.waitFor(10, TimeUnit.MINUTES);
            if (!done) {
                p.destroyForcibly();
                throw new BusinessException(StatusCode.FFMPEG_FAILURE);
            }
            if (p.exitValue() != 0) {
                String stderr = new String(p.getInputStream().readAllBytes());
                log.error("ffmpeg failed exit={} : {}", p.exitValue(), stderr);
                throw new BusinessException(StatusCode.STT_AUDIO_EXTRACT_FAILED);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(StatusCode.FFMPEG_FAILURE);
        } catch (IOException ex) {
            log.error("ffmpeg io error", ex);
            throw new BusinessException(StatusCode.FFMPEG_FAILURE);
        }
        return output;
    }
}
