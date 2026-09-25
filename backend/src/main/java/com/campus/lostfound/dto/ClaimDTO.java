package com.campus.lostfound.dto;

import lombok.Data;

/**
 * 发起认领请求
 */
@Data
public class ClaimDTO {

    private Long matchId;

    private String message;
}
