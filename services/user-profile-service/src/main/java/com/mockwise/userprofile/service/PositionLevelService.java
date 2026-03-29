package com.mockwise.userprofile.service;

import com.mockwise.userprofile.common.exception.BusinessException;
import com.mockwise.userprofile.common.util.StatusCode;
import com.mockwise.userprofile.dto.request.PositionLevelCreateRequest;
import com.mockwise.userprofile.dto.request.PositionLevelUpdateRequest;
import com.mockwise.userprofile.dto.response.PositionLevelResponse;
import com.mockwise.userprofile.entity.PositionLevel;
import com.mockwise.userprofile.mapper.PositionLevelMapper;
import com.mockwise.userprofile.repository.PositionLevelRepository;
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
public class PositionLevelService {

    PositionLevelRepository levelRepository;
    PositionLevelMapper levelMapper;

    public PositionLevelResponse create(PositionLevelCreateRequest request) {
        if (levelRepository.existsByPositionRoleIgnoreCase(request.positionRole())) {
            throw new BusinessException(StatusCode.LEVEL_ALREADY_EXISTS, request.positionRole());
        }

        PositionLevel entity = levelMapper.toEntity(request);
        PositionLevel saved = levelRepository.save(entity);
        return levelMapper.toResponse(saved);
    }

    public PositionLevelResponse update(String id, PositionLevelUpdateRequest request) {
        PositionLevel existing = levelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.LEVEL_NOT_FOUND, id));

        if (request.positionRole() != null &&
                !existing.getPositionRole().equalsIgnoreCase(request.positionRole()) &&
                levelRepository.existsByPositionRoleIgnoreCase(request.positionRole())) {
            throw new BusinessException(StatusCode.LEVEL_ALREADY_EXISTS, request.positionRole());
        }

        levelMapper.updateEntity(existing, request);
        PositionLevel updated = levelRepository.save(existing);
        return levelMapper.toResponse(updated);
    }

    public void delete(String id) {
        PositionLevel level = levelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.LEVEL_NOT_FOUND, id));

        try {
            levelRepository.delete(level);
        } catch (Exception e) {
            throw new BusinessException(StatusCode.OPERATION_NOT_SUPPORTED,
                    "Cannot delete level. It may be referenced by existing positions.");
        }
    }

    @Transactional(readOnly = true)
    public PositionLevelResponse getById(String id) {
        PositionLevel level = levelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.LEVEL_NOT_FOUND, id));
        return levelMapper.toResponse(level);
    }

    @Transactional(readOnly = true)
    public List<PositionLevelResponse> getAll() {
        return levelRepository.findAll()
                .stream()
                .map(levelMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PositionLevelResponse> getAllActive() {
        return levelRepository.findAllByActiveTrue()
                .stream()
                .map(levelMapper::toResponse)
                .toList();
    }

    public PositionLevelResponse toggleActive(String id) {
        PositionLevel level = levelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.LEVEL_NOT_FOUND, id));

        level.setActive(!level.getActive());
        PositionLevel saved = levelRepository.save(level);
        return levelMapper.toResponse(saved);
    }

    public PositionLevelResponse disable(String id) {
        PositionLevel level = levelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.LEVEL_NOT_FOUND, id));

        level.setActive(false);
        PositionLevel saved = levelRepository.save(level);
        return levelMapper.toResponse(saved);
    }

    public PositionLevelResponse enable(String id) {
        PositionLevel level = levelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.LEVEL_NOT_FOUND, id));

        level.setActive(true);
        PositionLevel saved = levelRepository.save(level);
        return levelMapper.toResponse(saved);
    }
}
