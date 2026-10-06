package com.ridehailing.logistics.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
  private String code;
  private String message;
  private T data;
  private String correlationId;

  public static <T> ApiResponse<T> success(T data, String correlationId) {
    return ApiResponse.<T>builder()
        .code("SUCCESS")
        .message("Request completed successfully")
        .data(data)
        .correlationId(correlationId)
        .build();
  }

  public static <T> ApiResponse<T> error(String code, String message, String correlationId) {
    return ApiResponse.<T>builder()
        .code(code)
        .message(message)
        .correlationId(correlationId)
        .build();
  }
}
