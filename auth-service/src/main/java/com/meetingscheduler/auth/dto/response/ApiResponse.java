package com.meetingscheduler.auth.dto.response;

public record ApiResponse<T>(boolean success, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data);
    }
}
