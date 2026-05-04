package com.mockwise.interview.client.storage;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.storage.dto.StorageObjectResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageAdapter {

    StorageClient client;

    public StorageObjectResponse getObject(UUID objectId) {
        return unwrap(client.getObject(objectId));
    }

    private static <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new BusinessException(StatusCode.STORAGE_UNAVAILABLE);
        }
        if (!response.isSuccess()) {
            throw new BusinessException(
                    response.getCode(),
                    response.getMessage() != null ? response.getMessage() : "storage call failed",
                    StatusCode.STORAGE_UNAVAILABLE.getHttpStatus());
        }
        return response.getData();
    }
}
