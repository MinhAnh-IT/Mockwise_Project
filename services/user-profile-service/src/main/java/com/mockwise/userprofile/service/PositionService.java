package com.mockwise.userprofile.service;

import com.mockwise.userprofile.common.exception.BusinessException;
import com.mockwise.userprofile.common.util.StatusCode;
import com.mockwise.userprofile.entity.Position;
import com.mockwise.userprofile.entity.PositionLevel;
import com.mockwise.userprofile.entity.PositionTrack;
import com.mockwise.userprofile.mapper.PositionMapper;
import com.mockwise.userprofile.repository.PositionLevelRepository;
import com.mockwise.userprofile.repository.PositionRepository;
import com.mockwise.userprofile.repository.PositionTrackRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal service for Position entity.
 * Position represents a combination of PositionTrack + PositionLevel.
 * Multiple users can share the same Position (e.g., many users can be "Backend - Senior").
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional
public class PositionService {

    PositionMapper positionMapper;
    PositionRepository positionRepository;
    PositionTrackRepository trackRepository;
    PositionLevelRepository levelRepository;

    public Position getOrCreatePosition(String trackId, String levelId) {
        log.debug("Getting or creating Position for trackId={}, levelId={}", trackId, levelId);

        PositionTrack track = trackRepository.findById(trackId)
                .orElseThrow(() -> new BusinessException(StatusCode.TRACK_NOT_FOUND, trackId));

        if (!Boolean.TRUE.equals(track.getActive())) {
            throw new BusinessException(StatusCode.TRACK_NOT_ACTIVE, track.getName());
        }

        PositionLevel level = levelRepository.findById(levelId)
                .orElseThrow(() -> new BusinessException(StatusCode.LEVEL_NOT_FOUND, levelId));

        if (!Boolean.TRUE.equals(level.getActive())) {
            throw new BusinessException(StatusCode.LEVEL_NOT_ACTIVE, level.getPositionRole());
        }

        return positionRepository.findByTrackIdAndLevelId(trackId, levelId)
                .orElseGet(() -> {
                    log.info("Creating new Position for track={}, level={}", track.getName(), level.getPositionRole());
                    Position newPosition = positionMapper.toEntity(track, level);
                    Position saved = positionRepository.save(newPosition);
                    return positionRepository.findByTrackIdAndLevelId(trackId, levelId)
                            .orElse(saved);
                });
    }
}
