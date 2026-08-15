package com.gojeom.storage;

import com.gojeom.common.response.ApiResponse;
import com.gojeom.common.security.UserPrincipal;
import com.gojeom.storage.StorageService.PresignedUpload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storageService;

    @PostMapping("/presigned")
    public ApiResponse<PresignedUpload> presigned(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody PresignedRequest request) {

        return ApiResponse.ok(storageService.presignUpload(
                request.purpose(), me.id(), request.contentType(), request.contentLength()));
    }

    public record PresignedRequest(
            @NotNull(message = "업로드 용도를 지정해주세요.")
            UploadPurpose purpose,

            @NotBlank(message = "파일 형식을 확인해주세요.")
            String contentType,

            @Positive(message = "파일 크기를 확인해주세요.")
            long contentLength) {
    }
}
