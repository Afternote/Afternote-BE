package com.afternote.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Schema(description = "프로필 수정 요청")
public class UserUpdateProfileRequest {

    public static final String OPTIONAL_CLEAR_DESCRIPTION =
            "필드를 생략하면 기존 값을 유지한다. null 또는 공백 문자열이면 비워 조회 시 null이 된다.";

    @Schema(description = "사용자 이름. 생략하거나 null이면 유지하고, 공백이면 거부한다.", example = "김소희", nullable = true)
    @Getter
    private String name;

    @Schema(
            description = "연락처. " + OPTIONAL_CLEAR_DESCRIPTION,
            example = "01012345678",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Getter
    private String phone;

    @Schema(
            description = "프로필 이미지 URL. " + OPTIONAL_CLEAR_DESCRIPTION,
            example = "https://cdn.example.com/profile/1.png",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Getter
    private String profileImageUrl;

    private boolean phoneSpecified;
    private boolean profileImageUrlSpecified;

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("phone")
    public void setPhone(String phone) {
        this.phoneSpecified = true;
        this.phone = blankToNull(phone);
    }

    @JsonProperty("profileImageUrl")
    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrlSpecified = true;
        this.profileImageUrl = blankToNull(profileImageUrl);
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isPhoneSpecified() {
        return phoneSpecified;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isProfileImageUrlSpecified() {
        return profileImageUrlSpecified;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
