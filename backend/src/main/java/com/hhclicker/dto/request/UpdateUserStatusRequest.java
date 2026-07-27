package com.hhclicker.dto.request;

import com.hhclicker.enumeration.UserStatus;

public record UpdateUserStatusRequest(UserStatus status) {
}
