package com.campus.lostfound.dto;

import lombok.Data;

/**
 * 个人资料编辑请求
 */
@Data
public class ProfileDTO {

    private String nickname;

    private String phone;

    private String email;

    private String avatar;
}
