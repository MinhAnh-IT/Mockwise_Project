package com.mockwise.userprofile.service;

import com.mockwise.userprofile.common.exception.BusinessException;
import com.mockwise.userprofile.common.util.StatusCode;
import com.mockwise.userprofile.dto.request.PositionTrackCreateRequest;
import com.mockwise.userprofile.dto.request.PositionTrackUpdateRequest;
import com.mockwise.userprofile.dto.response.PositionTrackResponse;
import com.mockwise.userprofile.entity.PositionTrack;
import com.mockwise.userprofile.mapper.PositionTrackMapper;
import com.mockwise.userprofile.repository.PositionTrackRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional
public class PositionTrackService {

    PositionTrackRepository trackRepository;
    PositionTrackMapper trackMapper;

    public PositionTrackResponse create(PositionTrackCreateRequest request) {
        if (trackRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException(StatusCode.TRACK_ALREADY_EXISTS, request.name());
        }

        PositionTrack entity = trackMapper.toEntity(request);
        PositionTrack saved = trackRepository.save(entity);
        return trackMapper.toResponse(saved);
    }

    public PositionTrackResponse update(String id, PositionTrackUpdateRequest request) {
        PositionTrack existing = trackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.TRACK_NOT_FOUND, id));

        if (request.name() != null &&
                !existing.getName().equalsIgnoreCase(request.name()) &&
                trackRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException(StatusCode.TRACK_ALREADY_EXISTS, request.name());
        }

        trackMapper.updateEntity(existing, request);
        PositionTrack updated = trackRepository.save(existing);
        return trackMapper.toResponse(updated);
    }

    public void delete(String id) {
        PositionTrack track = trackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.TRACK_NOT_FOUND, id));

        try {
            trackRepository.delete(track);
        } catch (Exception e) {
            throw new BusinessException(StatusCode.OPERATION_NOT_SUPPORTED,
                    "Cannot delete track. It may be referenced by existing positions.");
        }
    }

    @Transactional(readOnly = true)
    public PositionTrackResponse getById(String id) {
        PositionTrack track = trackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.TRACK_NOT_FOUND, id));
        return trackMapper.toResponse(track);
    }

    @Transactional(readOnly = true)
    public List<PositionTrackResponse> getAll() {
        return trackRepository.findAll()
                .stream()
                .map(trackMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PositionTrackResponse> getAllActive() {
        return trackRepository.findAllByActiveTrue()
                .stream()
                .map(trackMapper::toResponse)
                .toList();
    }

    public PositionTrackResponse toggleActive(String id) {
        PositionTrack track = trackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.TRACK_NOT_FOUND, id));

        track.setActive(!track.getActive());
        PositionTrack saved = trackRepository.save(track);
        return trackMapper.toResponse(saved);
    }

    public PositionTrackResponse disable(String id) {
        PositionTrack track = trackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.TRACK_NOT_FOUND, id));

        track.setActive(false);
        PositionTrack saved = trackRepository.save(track);
        return trackMapper.toResponse(saved);
    }

    public PositionTrackResponse enable(String id) {
        PositionTrack track = trackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.TRACK_NOT_FOUND, id));

        track.setActive(true);
        PositionTrack saved = trackRepository.save(track);
        return trackMapper.toResponse(saved);
    }
}
