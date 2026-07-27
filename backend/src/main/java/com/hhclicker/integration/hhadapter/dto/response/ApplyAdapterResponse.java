package com.hhclicker.integration.hhadapter.dto.response;

public record ApplyAdapterResponse(String status, Integer httpStatus, String topicId, String chatId, String errorCode) {
}
